package com.smallbiz.excel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.common.ApiException;

/**
 * 读取上传的表格：.xlsx 只读第一个工作表的单元格值（公式只取缓存的结果，不执行；不解析宏），
 * 或 .csv（UTF-8 / 带 BOM / GBK 自动识别）。第一行是表头，表头末尾的 * 会被去掉。
 */
public final class SheetReader {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 5000;

    static {
        // 防压缩炸弹：解压后与压缩前的大小比例不能过高，单个文件解压后不超过 100MB
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    /** rowNumber 为表格中的行号（表头是第 1 行）。 */
    public record RowData(int rowNumber, Map<String, String> values) {
        public String get(String header) {
            String v = values.get(header);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public record Parsed(List<String> headers, List<RowData> rows) {
    }

    private SheetReader() {
    }

    public static Parsed read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("IMPORT_FILE_EMPTY", "请选择要导入的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("IMPORT_FILE_TOO_LARGE", "文件不能超过 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("IMPORT_FILE_UNREADABLE", "文件读取失败，请重新选择");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) {
            throw ApiException.badRequest("IMPORT_FORMAT_UNSUPPORTED", "不支持旧版 .xls 文件，请在 Excel 中另存为 .xlsx 后再上传");
        }
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return readXlsx(bytes);
        }
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return readCsv(bytes);
        }
        throw ApiException.badRequest("IMPORT_FORMAT_UNSUPPORTED", "只支持 .xlsx 或 .csv 文件");
    }

    // ---------------- xlsx ----------------

    static Parsed readXlsx(byte[] bytes) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (wb.getNumberOfSheets() == 0) {
                throw ApiException.badRequest("IMPORT_FILE_EMPTY", "文件里没有工作表");
            }
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS + 1) {
                throw tooManyRows();
            }
            List<List<String>> table = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < Math.max(0, row.getLastCellNum()) && c < 50; c++) {
                        cells.add(cellText(row.getCell(c)));
                    }
                }
                table.add(cells);
            }
            return toParsed(table);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // 损坏的文件、压缩炸弹（超过解压比例）、加密文件等
            throw ApiException.badRequest("IMPORT_FILE_UNREADABLE", "无法读取这个 Excel 文件，请确认文件没有损坏、没有设置密码");
        }
    }

    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> "";
        };
    }

    // ---------------- csv ----------------

    static Parsed readCsv(byte[] bytes) {
        return toParsed(parseCsv(decode(bytes)));
    }

    /** UTF-8（含 BOM）优先；不是合法 UTF-8 时按 GBK（国内 Excel 另存 CSV 的默认编码）。 */
    static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS + 1) {
                    throw tooManyRows();
                }
            } else {
                cell.append(ch);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    // ---------------- 共用 ----------------

    private static Parsed toParsed(List<List<String>> table) {
        int headerIdx = -1;
        for (int i = 0; i < table.size(); i++) {
            if (table.get(i).stream().anyMatch(s -> !s.isBlank())) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) {
            throw ApiException.badRequest("IMPORT_FILE_EMPTY", "文件是空的，请按模板填写后再上传");
        }
        List<String> headers = table.get(headerIdx).stream().map(SheetReader::normalizeHeader).toList();
        List<RowData> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                if (!headers.get(c).isEmpty()) {
                    values.put(headers.get(c), c < cells.size() ? cells.get(c) : "");
                }
            }
            rows.add(new RowData(i + 1, values));
        }
        if (rows.size() > MAX_ROWS) {
            throw tooManyRows();
        }
        return new Parsed(headers, rows);
    }

    static String normalizeHeader(String h) {
        String s = h == null ? "" : h.replace(' ', ' ').trim();
        while (s.endsWith("*") || s.endsWith("＊")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        // 英文模板的表头也能识别
        return ExcelI18n.headerToChinese(s);
    }

    private static ApiException tooManyRows() {
        return ApiException.badRequest("IMPORT_TOO_MANY_ROWS", "一次最多导入 " + MAX_ROWS + " 行，请分成几个文件导入");
    }
}
