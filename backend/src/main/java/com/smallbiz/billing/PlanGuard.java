package com.smallbiz.billing;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.smallbiz.common.ApiException;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.TenantRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 套餐检查：既用于 @RequiresFeature 拦截器，也供业务代码直接调用。 */
@Component
public class PlanGuard implements HandlerInterceptor {

    private final TenantRepository tenants;

    public PlanGuard(TenantRepository tenants) {
        this.tenants = tenants;
    }

    /** 当前店铺实际生效的套餐（付费套餐过期后为 FREE）。 */
    public Plan currentPlan(Long tenantId) {
        return tenants.findById(tenantId).map(t -> t.effectivePlan(Instant.now())).orElse(Plan.FREE);
    }

    public void require(Long tenantId, Feature feature) {
        if (!PlanPolicy.allows(currentPlan(tenantId), feature)) {
            throw featureRequired(feature);
        }
    }

    public static ApiException featureRequired(Feature feature) {
        return new ApiException(HttpStatus.FORBIDDEN, "PLAN_FEATURE_REQUIRED",
                "「" + feature.label() + "」需要标准版，升级后即可使用",
                Map.of("feature", feature.name(), "requiredPlan", PlanPolicy.requiredPlan(feature).name()));
    }

    public static ApiException limitReached(String limit, int max) {
        return new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT_REACHED",
                "免费版最多 " + max + " 个商品，升级标准版可不限数量",
                Map.of("limit", limit, "max", max, "requiredPlan", Plan.STANDARD.name()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequiresFeature rf = hm.getMethodAnnotation(RequiresFeature.class);
        if (rf == null) {
            rf = hm.getBeanType().getAnnotation(RequiresFeature.class);
        }
        if (rf == null) {
            return true;
        }
        Long tenantId = CurrentUser.tenantIdOrNull();
        if (tenantId != null) {
            require(tenantId, rf.value());
        }
        return true;
    }
}
