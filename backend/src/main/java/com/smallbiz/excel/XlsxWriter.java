package com.smallbiz.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 生成 .xlsx：数字存为数字（金额两位小数）、时间按店铺时区、表头加粗并冻结首行。
 * 使用流式写入（SXSSF），大文件不会占满内存。文本以 = + - @ 开头时加 ' 前缀，防止公式注入。
 */
public final class XlsxWriter {

    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public enum Kind { TEXT, INT, MONEY, DECIMAL, DATETIME, DATE }

    public record Column(String header, Kind kind) {
        public static Column text(String h) {
            return new Column(h, Kind.TEXT);
        }

        public static Column integer(String h) {
            return new Column(h, Kind.INT);
        }

        public static Column money(String h) {
            return new Column(h, Kind.MONEY);
        }

        public static Column dateTime(String h) {
            return new Column(h, Kind.DATETIME);
        }

        public static Column date(String h) {
            return new Column(h, Kind.DATE);
        }
    }

    /** textFormat：整列设为文本格式（导入模板用，防止 Excel 把条码等长数字变成科学计数法）。 */
    public record SheetData(String name, List<Column> columns, List<List<Object>> rows, boolean textFormat) {
    }

    private final List<SheetData> sheets = new ArrayList<>();
    private final ZoneId zone;

    public XlsxWriter(ZoneId zone) {
        this.zone = zone;
    }

    public XlsxWriter sheet(String name, List<Column> columns, List<List<Object>> rows) {
        sheets.add(new SheetData(name, columns, rows, false));
        return this;
    }

    public XlsxWriter textSheet(String name, List<String> headers) {
        sheets.add(new SheetData(name, headers.stream().map(Column::text).toList(), List.of(), true));
        return this;
    }

    public byte[] toBytes() {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle dateTime = wb.createCellStyle();
            dateTime.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            for (SheetData sd : sheets) {
                Sheet sheet = wb.createSheet(ExcelI18n.t(sd.name()));
                int[] widths = new int[sd.columns().size()];
                Row hr = sheet.createRow(0);
                for (int c = 0; c < sd.columns().size(); c++) {
                    Cell cell = hr.createCell(c);
                    String h = ExcelI18n.t(sd.columns().get(c).header());
                    cell.setCellValue(h);
                    cell.setCellStyle(header);
                    widths[c] = displayWidth(h);
                }
                int r = 1;
                for (List<Object> values : sd.rows()) {
                    Row row = sheet.createRow(r++);
                    for (int c = 0; c < sd.columns().size() && c < values.size(); c++) {
                        Object v = values.get(c);
                        if (v == null) {
                            continue;
                        }
                        Cell cell = row.createCell(c);
                        Kind kind = sd.columns().get(c).kind();
                        switch (kind) {
                            case INT, DECIMAL -> cell.setCellValue(toDouble(v));
                            case MONEY -> {
                                cell.setCellValue(toDouble(v));
                                cell.setCellStyle(money);
                            }
                            case DATETIME -> {
                                cell.setCellValue(toLocalDateTime(v));
                                cell.setCellStyle(dateTime);
                            }
                            case DATE -> {
                                cell.setCellValue(v instanceof LocalDate d ? d : toLocalDateTime(v).toLocalDate());
                                cell.setCellStyle(date);
                            }
                            case TEXT -> cell.setCellValue(safeText(ExcelI18n.t(v.toString())));
                        }
                        widths[c] = Math.max(widths[c], kind == Kind.DATETIME ? 17 : kind == Kind.DATE ? 11
                                : displayWidth(v instanceof BigDecimal b ? b.toPlainString() : v.toString()));
                    }
                }
                sheet.createFreezePane(0, 1);
                for (int c = 0; c < widths.length; c++) {
                    sheet.setColumnWidth(c, Math.min(60, Math.max(widths[c] + 2, sd.textFormat() ? 14 : 0)) * 256);
                    if (sd.textFormat()) {
                        sheet.setDefaultColumnStyle(c, text);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ResponseEntity<byte[]> response(String filename) {
        return response(filename, toBytes());
    }

    public static ResponseEntity<byte[]> response(String filename, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ExcelI18n.filename(filename), StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX)
                .body(body);
    }

    /** 以 = + - @ 开头的文本在 Excel 中可能被当作公式：加单引号前缀。 */
    static String safeText(String s) {
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            return "'" + s;
        }
        return s;
    }

    private LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof Instant i) {
            return LocalDateTime.ofInstant(i, zone);
        }
        if (v instanceof LocalDateTime l) {
            return l;
        }
        if (v instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        throw new IllegalArgumentException("不支持的时间类型：" + v.getClass());
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return new BigDecimal(v.toString()).doubleValue();
    }

    /** 中文按 2 个字符宽度估算列宽。 */
    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length() && w < 60; i++) {
            w += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return w;
    }
}
