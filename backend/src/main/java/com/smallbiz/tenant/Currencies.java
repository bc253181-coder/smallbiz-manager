package com.smallbiz.tenant;

import java.util.LinkedHashMap;
import java.util.Map;

import com.smallbiz.common.ApiException;

/** 店铺可选的币种及其默认时区（开店时按币种带出时区）。 */
public final class Currencies {

    private Currencies() {
    }

    public static final Map<String, String> DEFAULT_TIMEZONE = new LinkedHashMap<>();

    static {
        DEFAULT_TIMEZONE.put("CNY", "Asia/Shanghai");
        DEFAULT_TIMEZONE.put("MYR", "Asia/Kuala_Lumpur");
        DEFAULT_TIMEZONE.put("SGD", "Asia/Singapore");
        DEFAULT_TIMEZONE.put("USD", "America/New_York");
        DEFAULT_TIMEZONE.put("HKD", "Asia/Hong_Kong");
        DEFAULT_TIMEZONE.put("TWD", "Asia/Taipei");
        DEFAULT_TIMEZONE.put("THB", "Asia/Bangkok");
    }

    public static String require(String currency) {
        String c = currency == null ? "" : currency.trim().toUpperCase();
        if (!DEFAULT_TIMEZONE.containsKey(c)) {
            throw ApiException.badRequest("UNSUPPORTED_CURRENCY", "不支持这个币种");
        }
        return c;
    }
}
