package com.smallbiz.tenant;

import com.smallbiz.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 用户与商户的成员关系。
 * 不继承 TenantScopedEntity：登录时需要跨商户查询"我加入了哪些商户"。
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

    protected TenantMember() {
    }

    public TenantMember(Long tenantId, Long userId, MemberRole role) {
        this.tenantId = tenantId;
        this.userId = userId;
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
}
