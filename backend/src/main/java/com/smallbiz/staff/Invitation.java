package com.smallbiz.staff;

import java.time.Instant;

import com.smallbiz.common.BaseEntity;
import com.smallbiz.tenant.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 员工邀请链接。数据库只存令牌的 SHA-256 摘要，原文只在创建时返回一次。
 * 不继承 TenantScopedEntity：被邀请人接受时还不是这家店的成员，需要按令牌跨店查询；
 * 店内查询一律显式带 tenant_id。
 */
@Entity
@Table(name = "invitations")
public class Invitation extends BaseEntity {

    public enum Status { PENDING, ACCEPTED, REVOKED }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(length = 50)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Invitation() {
    }

    public Invitation(Long tenantId, String tokenHash, MemberRole role, String note, Instant expiresAt, Long createdBy) {
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.role = role;
        this.note = note;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /** 未使用、未撤销、未过期。 */
    public boolean isUsable(Instant now) {
        return status == Status.PENDING && expiresAt.isAfter(now);
    }

    public void accept(Long userId, Instant now) {
        this.status = Status.ACCEPTED;
        this.acceptedBy = userId;
        this.acceptedAt = now;
    }

    public void revoke() {
        this.status = Status.REVOKED;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public MemberRole getRole() {
        return role;
    }

    public String getNote() {
        return note;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
