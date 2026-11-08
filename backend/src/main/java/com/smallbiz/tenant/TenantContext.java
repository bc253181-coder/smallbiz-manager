package com.smallbiz.tenant;

import java.util.function.Supplier;

/**
 * 在当前线程内临时指定商户（例如刚创建商户、尚未换发令牌时，或测试中）。
 * 正常请求中商户来自登录令牌，不需要使用此类。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private TenantContext() {
    }

    static Long override() {
        return OVERRIDE.get();
    }

    public static <T> T runAs(Long tenantId, Supplier<T> action) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    public static void runAs(Long tenantId, Runnable action) {
        runAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
