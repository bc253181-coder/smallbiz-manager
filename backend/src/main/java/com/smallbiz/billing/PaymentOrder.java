package com.smallbiz.billing;

import java.math.BigDecimal;
import java.time.Instant;

import com.smallbiz.common.BaseEntity;
import com.smallbiz.tenant.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 扫码付款单。
 *
 * <p>注意：不继承 TenantScopedEntity（平台管理员需要跨商户审核），
 * 商户端的所有查询/操作都在 PaymentService 中显式校验 tenantId。
 */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends BaseEntity {

    public enum Status { AWAITING_PAYMENT, PENDING_REVIEW, APPROVED, REJECTED, EXPIRED, CANCELLED }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "order_no", nullable = false, length = 30, updatable = false)
    private String orderNo;

    @Column(name = "reference_code", nullable = false, length = 12, updatable = false)
    private String referenceCode;

    @Column(name = "plan_price_id", nullable = false)
    private Long planPriceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanPrice.Period period;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 站内扫码付款的收款方式；在线支付时为空。 */
    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    /** 在线支付渠道（例如 MOCK）；扫码付款时为空。 */
    @Column(length = 30, updatable = false)
    private String channel;

    /** 支付渠道的交易号（同一渠道内唯一，防止重复开通）。 */
    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    /** 支付渠道的付款页地址。 */
    @Column(name = "checkout_ref", length = 200)
    private String checkoutRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** 需要平台人工处理的异常（金额不符、过期后付款、重复付款等）。 */
    @Column(length = 200)
    private String anomaly;

    @Column(name = "anomaly_resolved_at")
    private Instant anomalyResolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AWAITING_PAYMENT;

    @Column(name = "proof_image_path", length = 300)
    private String proofImagePath;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "payer_note", length = 300)
    private String payerNote;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private int version;

    protected PaymentOrder() {
    }

    public PaymentOrder(Long tenantId, String orderNo, String referenceCode, PlanPrice price, PaymentMethod method,
            Instant expiresAt, Long createdBy) {
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.referenceCode = referenceCode;
        this.planPriceId = price.getId();
        this.plan = price.getPlan();
        this.period = price.getPeriod();
        this.durationDays = price.getDurationDays();
        this.currency = price.getCurrency();
        this.amount = price.getAmount();
        this.paymentMethodId = method.getId();
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /** 在线支付付款单。 */
    public static PaymentOrder online(Long tenantId, String orderNo, String referenceCode, PlanPrice price, String channel,
            Instant expiresAt, Long createdBy) {
        PaymentOrder o = new PaymentOrder();
        o.tenantId = tenantId;
        o.orderNo = orderNo;
        o.referenceCode = referenceCode;
        o.planPriceId = price.getId();
        o.plan = price.getPlan();
        o.period = price.getPeriod();
        o.durationDays = price.getDurationDays();
        o.currency = price.getCurrency();
        o.amount = price.getAmount();
        o.channel = channel;
        o.expiresAt = expiresAt;
        o.createdBy = createdBy;
        return o;
    }

    public boolean isOnline() {
        return channel != null;
    }

    public void setCheckoutRef(String checkoutRef) {
        this.checkoutRef = checkoutRef;
    }

    /** 在线支付成功并已开通。note 不为空时记为异常（如过期后才付款），开通照常。 */
    public void markPaid(String txnId, Instant paidAt, Instant now, String note) {
        this.providerTxnId = txnId;
        this.paidAt = paidAt;
        this.status = Status.APPROVED;
        this.reviewedAt = now;
        if (note != null) {
            addAnomaly(note);
        }
    }

    /** 收到付款但不能开通（金额不符等）：记录交易号和原因，状态不变，由平台处理。 */
    public void markPaidWithProblem(String txnId, Instant paidAt, String reason) {
        this.providerTxnId = txnId;
        this.paidAt = paidAt;
        addAnomaly(reason);
    }

    public void addAnomaly(String reason) {
        String s = anomaly == null ? reason : anomaly + "；" + reason;
        this.anomaly = s.length() > 200 ? s.substring(0, 200) : s;
        this.anomalyResolvedAt = null;
    }

    public void resolveAnomaly(Instant now) {
        this.anomalyResolvedAt = now;
    }

    /** 待付款且已过期：在读取或操作时惰性标记为 EXPIRED。 */
    public boolean expireIfDue(Instant now) {
        if (status == Status.AWAITING_PAYMENT && expiresAt.isBefore(now)) {
            status = Status.EXPIRED;
            return true;
        }
        return false;
    }

    public void submitProof(String imagePath, String transactionRef, String note, Instant now) {
        this.proofImagePath = imagePath;
        this.transactionRef = transactionRef;
        this.payerNote = note;
        this.submittedAt = now;
        this.status = Status.PENDING_REVIEW;
    }

    public void approve(Long adminId, Instant now) {
        this.status = Status.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
    }

    public void reject(Long adminId, String reason, Instant now) {
        this.status = Status.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.rejectReason = reason;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public boolean isOpen() {
        return status == Status.AWAITING_PAYMENT || status == Status.PENDING_REVIEW;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public Plan getPlan() {
        return plan;
    }

    public PlanPrice.Period getPeriod() {
        return period;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getPaymentMethodId() {
        return paymentMethodId;
    }

    public Status getStatus() {
        return status;
    }

    public String getProofImagePath() {
        return proofImagePath;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getPayerNote() {
        return payerNote;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getChannel() {
        return channel;
    }

    public String getProviderTxnId() {
        return providerTxnId;
    }

    public String getCheckoutRef() {
        return checkoutRef;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getAnomaly() {
        return anomaly;
    }

    public Instant getAnomalyResolvedAt() {
        return anomalyResolvedAt;
    }

    /** 下单的账号（永久会员开通给这个账号）。 */
    public Long getCreatedBy() {
        return createdBy;
    }

    public boolean isLifetime() {
        return period == PlanPrice.Period.LIFETIME;
    }
}
