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

/** 激活码：兑换只比对哈希；配置了密钥时原文加密保存，供发码台取码（docs/17）。 */
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

    /** 加密的原文（IV + 密文）；以前生成的旧码为空。 */
    @Column(name = "code_encrypted")
    private byte[] codeEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UNUSED;

    @Column(name = "redeemed_tenant_id")
    private Long redeemedTenantId;

    @Column(name = "redeemed_by")
    private Long redeemedBy;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    /** 发给了哪个小红书订单。 */
    @Column(name = "order_ref", length = 100)
    private String orderRef;

    @Column(name = "dispensed_at")
    private Instant dispensedAt;

    @Column(name = "dispensed_by")
    private Long dispensedBy;

    protected ActivationCode() {
    }

    public ActivationCode(Long batchId, String codeHash, String codeHint, byte[] codeEncrypted) {
        this.batchId = batchId;
        this.codeHash = codeHash;
        this.codeHint = codeHint;
        this.codeEncrypted = codeEncrypted;
    }

    public void dispense(String orderRef, Long adminId, Instant now) {
        this.orderRef = orderRef;
        this.dispensedBy = adminId;
        this.dispensedAt = now;
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

    public byte[] getCodeEncrypted() {
        return codeEncrypted;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public Instant getDispensedAt() {
        return dispensedAt;
    }

    public Long getDispensedBy() {
        return dispensedBy;
    }

    public Long getRedeemedBy() {
        return redeemedBy;
    }
}
