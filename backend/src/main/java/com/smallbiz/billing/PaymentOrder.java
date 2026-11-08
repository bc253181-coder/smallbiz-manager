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

    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

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
}
