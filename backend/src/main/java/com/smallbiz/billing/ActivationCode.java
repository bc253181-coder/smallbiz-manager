package com.smallbiz.billing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 激活码：数据库只保存哈希，原文只在生成时导出一次。 */
@Entity
@Table(name = "activation_codes")
public class ActivationCode {

    public enum Status { UNUSED, REDEEMED, VOIDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "code_hint", nullable = false, length = 8)
    private String codeHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UNUSED;

    @Column(name = "redeemed_tenant_id")
    private Long redeemedTenantId;

    @Column(name = "redeemed_by")
    private Long redeemedBy;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    protected ActivationCode() {
    }

    public ActivationCode(Long batchId, String codeHash, String codeHint) {
        this.batchId = batchId;
        this.codeHash = codeHash;
        this.codeHint = codeHint;
    }

    public void redeem(Long tenantId, Long userId, Instant now) {
        this.status = Status.REDEEMED;
        this.redeemedTenantId = tenantId;
        this.redeemedBy = userId;
        this.redeemedAt = now;
    }

    public void voidCode() {
        this.status = Status.VOIDED;
    }

    public Long getId() {
        return id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getCodeHint() {
        return codeHint;
    }

    public Status getStatus() {
        return status;
    }

    public Long getRedeemedTenantId() {
        return redeemedTenantId;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
