package com.smallbiz.billing;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

/**
 * 套餐开通 / 续期的唯一入口（激活码、扫码付款审核、平台管理员手动调整都走这里）。
 * 对店铺行加锁，避免并发开通时到期时间计算错误；每次变更写一条 PlanChangeLog。
 */
@Service
public class PlanService {

    private final TenantRepository tenants;
    private final PlanChangeLogRepository logs;

    public PlanService(TenantRepository tenants, PlanChangeLogRepository logs) {
        this.tenants = tenants;
        this.logs = logs;
    }

    /**
     * 开通或续期。同一套餐且未到期：从原到期时间往后顺延；否则从现在开始计算。
     *
     * @param trial 是否首月体验（每个店铺限一次）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Tenant grant(Long tenantId, Plan plan, int days, boolean trial, PlanChangeLog.Source source,
            Long activationCodeId, Long paymentOrderId, Long operatorId, String note) {
        Tenant t = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        if (trial && t.isTrialUsed()) {
            throw trialUsed();
        }
        Instant now = Instant.now();
        Plan oldPlan = t.getPlan();
        Instant oldExpires = t.getPlanExpiresAt();
        boolean sameActive = t.effectivePlan(now) == plan && oldExpires != null && oldExpires.isAfter(now);
        Instant start = sameActive ? oldExpires : now;
        Instant newExpires = start.plus(Duration.ofDays(days));
        t.changePlan(plan, newExpires);
        if (trial) {
            t.markTrialUsed();
        }
        logs.save(new PlanChangeLog(tenantId, source, activationCodeId, paymentOrderId, oldPlan, plan, oldExpires,
                newExpires, operatorId, note));
        return t;
    }

    /** 平台管理员直接设置套餐和到期时间（处理退款、补偿等特殊情况）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Tenant setByAdmin(Long tenantId, Plan plan, Instant expiresAt, Long adminId, String note) {
        Tenant t = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        if (plan != Plan.FREE && (expiresAt == null || !expiresAt.isAfter(Instant.now()))) {
            throw ApiException.badRequest("INVALID_EXPIRY", "付费套餐的到期时间必须晚于现在");
        }
        Plan oldPlan = t.getPlan();
        Instant oldExpires = t.getPlanExpiresAt();
        t.changePlan(plan, expiresAt);
        logs.save(new PlanChangeLog(tenantId, PlanChangeLog.Source.ADMIN_MANUAL, null, null, oldPlan, plan,
                oldExpires, t.getPlanExpiresAt(), adminId, note));
        return t;
    }

    public static ApiException trialUsed() {
        return new ApiException(HttpStatus.CONFLICT, "TRIAL_ALREADY_USED", "该店铺已经使用过首月体验，请选择月付或年付");
    }
}
