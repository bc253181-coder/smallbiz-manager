package com.smallbiz.receivable;

import java.math.BigDecimal;
import java.time.Instant;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 收款记录：只能新增和作废，不能修改或删除。 */
@Entity
@Table(name = "customer_payments")
public class CustomerPayment extends TenantScopedEntity {

    public enum Status { VALID, VOIDED }

    public enum Method { CASH, WECHAT, ALIPAY, BANK, TNG, DUITNOW, OTHER }

    @Column(name = "payment_no", nullable = false, length = 30, updatable = false)
    private String paymentNo;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Method method;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(length = 500, updatable = false)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.VALID;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", length = 200)
    private String voidReason;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Version
    private int version;

    protected CustomerPayment() {
    }

    public CustomerPayment(String paymentNo, Long customerId, BigDecimal amount, Method method, Instant receivedAt,
            String notes, Long createdBy) {
        this.paymentNo = paymentNo;
        this.customerId = customerId;
        this.amount = amount;
        this.method = method;
        this.receivedAt = receivedAt;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void markVoided(Instant now, Long userId, String reason) {
        this.status = Status.VOIDED;
        this.voidedAt = now;
        this.voidedBy = userId;
        this.voidReason = reason;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Method getMethod() {
        return method;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Long getVoidedBy() {
        return voidedBy;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}