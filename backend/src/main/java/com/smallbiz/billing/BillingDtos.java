package com.smallbiz.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.tenant.Plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BillingDtos {

    private BillingDtos() {
    }

    /** proUpgradeBonusDays：现在升级专业版时，标准版剩余时间可折算的专业版天数（非标准版时为 0）。 */
    public record CurrentPlan(Plan plan, Instant expiresAt, boolean trialUsed, long productCount, Integer maxProducts,
            Map<Feature, Boolean> features, int proUpgradeBonusDays) {
    }

    public record PriceView(Long id, Plan plan, PlanPrice.Period period, int durationDays, String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, boolean enabled) {
        public static PriceView of(PlanPrice p) {
            return new PriceView(p.getId(), p.getPlan(), p.getPeriod(), p.getDurationDays(), p.getCurrency(),
                    p.getAmount(), p.isEnabled());
        }
    }

    public record MethodView(Long id, String code, String displayName, String currency, String qrImageUrl,
            String payeeName, String instructions, boolean enabled) {
        public static MethodView of(PaymentMethod m) {
            return new MethodView(m.getId(), m.getCode(), m.getDisplayName(), m.getCurrency(),
                    ImageStorage.url(m.getQrImagePath()), m.getPayeeName(), m.getInstructions(), m.isEnabled());
        }
    }

    public record RedeemRequest(@NotBlank(message = "请输入激活码") @Size(max = 40, message = "激活码格式不正确") String code) {
    }

    public record CreatePaymentRequest(@NotNull(message = "请选择套餐") Long priceId,
            @NotNull(message = "请选择付款方式") Long paymentMethodId) {
    }

    public record PaymentOrderView(
            Long id,
            String orderNo,
            String referenceCode,
            Plan plan,
            PlanPrice.Period period,
            int durationDays,
            String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            PaymentOrder.Status status,
            MethodView method,
            Instant expiresAt,
            Instant submittedAt,
            Instant reviewedAt,
            String rejectReason,
            String transactionRef,
            String payerNote,
            boolean hasProof,
            Instant createdAt,
            Long tenantId,
            String tenantName,
            /** 在线支付渠道（扫码付款时为空）。 */
            String channel,
            Instant paidAt,
            String providerTxnId,
            /** 继续付款的地址（只在待付款的在线付款单上有）。 */
            String checkoutUrl,
            String anomaly,
            Instant anomalyResolvedAt) {

        public static PaymentOrderView of(PaymentOrder o, PaymentMethod m, String tenantName) {
            return new PaymentOrderView(o.getId(), o.getOrderNo(), o.getReferenceCode(), o.getPlan(), o.getPeriod(),
                    o.getDurationDays(), o.getCurrency(), o.getAmount(), o.getStatus(), m == null ? null : MethodView.of(m),
                    o.getExpiresAt(), o.getSubmittedAt(), o.getReviewedAt(), o.getRejectReason(), o.getTransactionRef(),
                    o.getPayerNote(), o.getProofImagePath() != null, o.getCreatedAt(), o.getTenantId(), tenantName,
                    o.getChannel(), o.getPaidAt(), o.getProviderTxnId(),
                    o.getStatus() == PaymentOrder.Status.AWAITING_PAYMENT ? o.getCheckoutRef() : null, o.getAnomaly(),
                    o.getAnomalyResolvedAt());
        }
    }

    public record OnlinePaymentRequest(@NotNull(message = "请选择套餐") Long priceId,
            @NotBlank(message = "请选择支付方式") @Size(max = 30) String channel) {
    }

    public record OnlinePaymentStarted(PaymentOrderView order, String checkoutUrl) {
    }

    public record PlanLogView(Long id, PlanChangeLog.Source source, Plan oldPlan, Plan newPlan, Instant oldExpiresAt,
            Instant newExpiresAt, String note, Instant createdAt) {
        public static PlanLogView of(PlanChangeLog l) {
            return new PlanLogView(l.getId(), l.getSource(), l.getOldPlan(), l.getNewPlan(), l.getOldExpiresAt(),
                    l.getNewExpiresAt(), l.getNote(), l.getCreatedAt());
        }
    }
}
