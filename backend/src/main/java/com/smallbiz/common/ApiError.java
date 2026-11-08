package com.smallbiz.common;

import java.util.Map;

/** 统一错误响应体。 */
public record ApiError(String code, String message, Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
