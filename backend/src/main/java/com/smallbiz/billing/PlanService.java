package com.smallbiz.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

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
 *
 * <p>规则（docs/06-receivables.md 第 3 节）：
 * <ul>
 * <li>同一套餐且未到期：从原到期时间往后顺延</li>
 * <li>升级（如标准版 → 专业版）：新套餐从现在开始，原套餐剩余天数按月价折算后追加，不会浪费</li>
 * <li>高等级套餐有效期内不能开通低等级套餐（409 PLAN_DOWNGRADE_NOT_ALLOWED）</li>
 * </ul>
 */
@Service
public class PlanService {

    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400);

    private final TenantRepository tenants;
    private final PlanChangeLogRepository logs;
    private final PlanPriceRepository prices;

    public PlanService(TenantRepository tenants, PlanChangeLogRepository logs, PlanPriceRepository prices) {
        this.tenants = tenants;
        this.logs = logs;
        this.prices = prices;
    }

    /**
     * 开通或续期。
     *
     * @param trial 是否首月体验（每个店铺限一次）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Tenant grant(Long tenantId, Plan plan, int days, boolean trial, PlanChangeLog.Source source,
            Long activationCodeId, Long paymentOrderId, Long operatorId, String note) {
        Tenant t = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        if (t.isLifetime()) {
            // 永久有效的店铺不能再按天数开通（否则会被改成有到期时间）
            throw ApiException.conflict("ALREADY_LIFETIME", "已经是高级永久会员，无需再开通");
        }
        if (trial && t.isTrialUsed()) {
            throw trialUsed();
        }
        Instant now = Instant.now();
        Plan current = t.effectivePlan(now);
        checkNotDowngrade(current, plan);

        Plan oldPlan = t.getPlan();
        Instant oldExpires = t.getPlanExpiresAt();
        boolean sameActive = current == plan && oldExpires != null && oldExpires.isAfter(now);
        Instant start = sameActive ? oldExpires : now;
        int bonus = sameActive ? 0 : upgradeBonusDays(t, plan, now);
        Instant newExpires = start.plus(Duration.ofDays((long) days + bonus));
        t.changePlan(plan, newExpires);
        if (trial) {
            t.markTrialUsed();
        }
        String fullNote = bonus > 0
                ? note + "（" + PlanPolicy.label(current) + "剩余时间折算为" + PlanPolicy.label(plan) + " " + bonus + " 天）"
                : note;
        logs.save(new PlanChangeLog(tenantId, source, activationCodeId, paymentOrderId, oldPlan, plan, oldExpires,
                newExpires, operatorId, fullNote));
        return t;
    }

    /**
     * 升级时，当前套餐剩余时间折算成目标套餐的天数：剩余天数 × 当前套餐月价 ÷ 目标套餐月价，向下取整。
     * 例：标准版剩 20 天，6.99 → 9.9：20 × 6.99 ÷ 9.9 = 14.1 → 14 天。不是升级时返回 0。
     */
    public int upgradeBonusDays(Tenant t, Plan target, Instant now) {
        Plan current = t.effectivePlan(now);
        if (current == Plan.FREE || PlanPolicy.rank(target) <= PlanPolicy.rank(current)
                || t.getPlanExpiresAt() == null || !t.getPlanExpiresAt().isAfter(now)) {
            return 0;
        }
        BigDecimal remainingDays = BigDecimal.valueOf(Duration.between(now, t.getPlanExpiresAt()).toSeconds())
                .divide(SECONDS_PER_DAY, 6, RoundingMode.DOWN);
        BigDecimal from = monthlyPrice(current, t.getCurrency());
        BigDecimal to = monthlyPrice(target, t.getCurrency());
        BigDecimal converted = (from == null || to == null || to.signum() <= 0)
                ? remainingDays
                : remainingDays.multiply(from).divide(to, 6, RoundingMode.DOWN);
        return converted.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    /** 按店铺币种取月价；该币种没有时用人民币价格。 */
    private BigDecimal monthlyPrice(Plan plan, String currency) {
        return prices.findFirstByPlanAndPeriodAndCurrency(plan, PlanPrice.Period.MONTH, currency)
                .or(() -> prices.findFirstByPlanAndPeriodAndCurrency(plan, PlanPrice.Period.MONTH, "CNY"))
                .map(PlanPrice::getAmount)
                .orElse(null);
    }

    /** 当前生效的套餐等级高于要开通的套餐时拒绝（避免专业版剩余时间被标准版覆盖）。 */
    public static void checkNotDowngrade(Plan current, Plan target) {
        if (PlanPolicy.rank(current) > PlanPolicy.rank(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_DOWNGRADE_NOT_ALLOWED",
                    "你当前是" + PlanPolicy.label(current) + "，请续费" + PlanPolicy.label(current) + "；到期后可以改买"
                            + PlanPolicy.label(target),
                    Map.of("currentPlan", current.name(), "targetPlan", target.name()));
        }
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
