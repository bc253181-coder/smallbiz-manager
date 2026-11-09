package com.smallbiz.tenant;

import java.time.Instant;

import com.smallbiz.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 用户与商户的成员关系。
 * 不继承 TenantScopedEntity：登录时需要跨商户查询"我加入了哪些商户"。
 * 被移除后保留记录（status = REMOVED）；再次受邀加入时复用这条记录。
 */
@Entity
@Table(name = "tenant_members")
public class TenantMember extends BaseEntity {

    public enum Status { ACTIVE, INVITED, REMOVED }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private Long removedBy;

    protected TenantMember() {
    }

    public TenantMember(Long tenantId, Long userId, MemberRole role) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
    }

    /** 被移除或退出后再次加入。 */
    public void rejoin(MemberRole role, Instant now) {
        this.role = role;
        this.status = Status.ACTIVE;
        this.joinedAt = now;
        this.removedAt = null;
        this.removedBy = null;
    }

    public void remove(Long byUserId, Instant now) {
        this.status = Status.REMOVED;
        this.removedAt = now;
        this.removedBy = byUserId;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
