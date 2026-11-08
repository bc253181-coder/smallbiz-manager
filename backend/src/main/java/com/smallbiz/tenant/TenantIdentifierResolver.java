package com.smallbiz.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.smallbiz.security.TokenService;

/**
 * Hibernate 在每次打开 Session（即每个事务开始）时调用本类，得到"当前商户"。
 *
 * <p>优先级：{@link TenantContext} 临时指定 → 登录令牌中的商户 →
 * {@link #NO_TENANT}（0，不存在的商户，因此查询结果为空——默认安全）。
 *
 * <p>由 Hibernate 通过类名实例化（见 application.yml），所以不是 Spring Bean、必须无状态。
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    public static final Long NO_TENANT = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long override = TenantContext.override();
        if (override != null) {
            return override;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Object tid = jwt.getClaims().get(TokenService.CLAIM_TENANT_ID);
            if (tid instanceof Number n) {
                return n.longValue();
            }
            if (tid instanceof String s && !s.isBlank()) {
                return Long.valueOf(s);
            }
        }
        return NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
