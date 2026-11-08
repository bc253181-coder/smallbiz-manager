package com.smallbiz.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.MemberRole;

/** 读取当前登录用户的信息（来自已验证的 JWT）。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw ApiException.unauthorized("UNAUTHENTICATED", "请先登录");
    }

    public static Long userId() {
        return Long.valueOf(jwt().getSubject());
    }

    /** 当前商户 id；未选择商户时为 null。 */
    public static Long tenantIdOrNull() {
        Object tid = jwt().getClaims().get(TokenService.CLAIM_TENANT_ID);
        if (tid instanceof Number n) {
            return n.longValue();
        }
        return tid == null ? null : Long.valueOf(tid.toString());
    }

    /** 需要已选择商户的接口调用此方法。 */
    public static Long requireTenantId() {
        Long tid = tenantIdOrNull();
        if (tid == null) {
            throw ApiException.forbidden("TENANT_REQUIRED", "请先创建或选择店铺");
        }
        return tid;
    }

    public static MemberRole roleOrNull() {
        String role = jwt().getClaimAsString(TokenService.CLAIM_ROLE);
        return role == null ? null : MemberRole.valueOf(role);
    }
}
