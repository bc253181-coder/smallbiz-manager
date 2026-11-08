package com.smallbiz.common;

public final class Strings {

    private Strings() {
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 转义 LIKE 通配符，配合 cb.like(..., '\\') 使用。 */
    public static String likeContains(String s) {
        return "%" + s.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
