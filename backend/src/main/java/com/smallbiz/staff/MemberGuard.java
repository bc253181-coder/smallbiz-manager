package com.smallbiz.staff;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.smallbiz.auth.AuthService;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.common.ApiException;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;
import com.smallbiz.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 每个带店铺的请求都核对成员身份，使移除员工、修改角色、专业版到期立即生效，
 * 不必等访问令牌（2 小时）过期：
 * <ul>
 * <li>已不是成员，或角色与令牌不一致 → 401 MEMBERSHIP_CHANGED（前端刷新令牌后拿到新身份）</li>
 * <li>员工所在店铺的专业版已到期 → 403 STORE_PLAN_EXPIRED</li>
 * </ul>
 * 登录、刷新、切换店铺、接受邀请、退出店铺等接口不经过此检查（见 WebConfig）。
 */
@Component
public class MemberGuard implements HandlerInterceptor {

    private final TenantMemberRepository members;
    private final TenantRepository tenants;

    public MemberGuard(TenantMemberRepository members, TenantRepository tenants) {
        this.members = members;
        this.tenants = tenants;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getUserPrincipal() == null) {
            return true; // 公开接口
        }
        Long tenantId = CurrentUser.tenantIdOrNull();
        if (tenantId == null) {
            return true;
        }
        MemberRole tokenRole = CurrentUser.roleOrNull();
        TenantMember m = members.findByTenantIdAndUserIdAndStatus(tenantId, CurrentUser.userId(), TenantMember.Status.ACTIVE)
                .orElse(null);
        if (m == null || m.getRole() != tokenRole) {
            throw ApiException.unauthorized("MEMBERSHIP_CHANGED", "你在这家店铺的权限已变更，请重新进入");
        }
        if (m.getRole() != MemberRole.OWNER) {
            Tenant t = tenants.findById(tenantId).orElse(null);
            if (t == null || !PlanPolicy.memberCanEnter(t.effectivePlan(Instant.now()), m.getRole())) {
                throw AuthService.storePlanExpired();
            }
        }
        return true;
    }
}
