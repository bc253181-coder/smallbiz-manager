package com.smallbiz.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 生成 Excel 可直接打开的 CSV（UTF-8 带 BOM，防止中文乱码；防公式注入）。 */
final class CsvWriter {

    private CsvWriter() {
    }

    static ResponseEntity<byte[]> response(String filename, List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        appendRow(sb, header.stream().map(h -> (Object) h).toList());
        rows.forEach(r -> appendRow(sb, r));
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static void appendRow(StringBuilder sb, List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        // 以 = + - @ 开头的文本在 Excel 中会被当作公式执行：加单引号前缀
        if (!(value instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
