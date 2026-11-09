package com.smallbiz.excel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 导出文件和导入模板的英文：英文界面下载时请求带 lang=en，表头、工作表名、固定的状态文字、
 * 模板说明和文件名按 resources/i18n/excel_en.tsv 翻译。导入时英文表头也能识别。
 */
public final class ExcelI18n {

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> ZH_BY_EN = new HashMap<>();
    private static final List<String> PREFIXES;

    static {
        try (var in = ExcelI18n.class.getResourceAsStream("/i18n/excel_en.tsv");
                var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).forEach(l -> {
                int tab = l.indexOf('\t');
                if (tab > 0) {
                    String zh = l.substring(0, tab);
                    String en = l.substring(tab + 1).trim();
                    EN.put(zh, en);
                    ZH_BY_EN.putIfAbsent(en.toLowerCase(Locale.ROOT), zh);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PREFIXES = EN.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    private ExcelI18n() {
    }

    /** 当前请求是否要英文（?lang=en）。 */
    public static boolean en() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            return "en".equalsIgnoreCase(a.getRequest().getParameter("lang"));
        }
        return false;
    }

    /** 英文时翻译（没有对照的原样返回）。 */
    public static String t(String zh) {
        return zh != null && en() ? EN.getOrDefault(zh, zh) : zh;
    }

    /** 文件名：「商品_2026-11-09.xlsx」→「Products_2026-11-09.xlsx」。 */
    public static String filename(String zh) {
        if (!en()) {
            return zh;
        }
        for (String k : PREFIXES) {
            if (zh.startsWith(k)) {
                return EN.get(k) + zh.substring(k.length()).replace("_全部_", "_all_").replace("开始_", "start_");
            }
        }
        return zh;
    }

    /** 导入：英文表头 → 中文表头（不区分大小写）；中文表头原样返回。 */
    public static String headerToChinese(String header) {
        return ZH_BY_EN.getOrDefault(header.toLowerCase(Locale.ROOT), header);
    }
}
