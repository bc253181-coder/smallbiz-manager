package com.smallbiz.auth;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 刷新令牌：数据库只保存 SHA-256 哈希，原文只在 HttpOnly Cookie 中。 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** 上次选择的商户，刷新时沿用。 */
    @Column(name = "current_tenant_id")
    private Long currentTenantId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** 因轮换而作废的时间；退出登录/改密码作废时为空（不享受宽限期）。 */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, Long currentTenantId, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.currentTenantId = currentTenantId;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** 退出登录：立即彻底作废。 */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
        rotatedAt = null;
    }

    /** 轮换：作废，但在宽限期内仍可再用一次换取新令牌。 */
    public void rotate(Instant now) {
        revokedAt = now;
        rotatedAt = now;
    }

    /** 刚被轮换（宽限期内）且未过期。 */
    public boolean isInRotationGrace(Instant now, java.time.Duration grace) {
        return rotatedAt != null && rotatedAt.plus(grace).isAfter(now) && expiresAt.isAfter(now);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCurrentTenantId() {
        return currentTenantId;
    }
}
