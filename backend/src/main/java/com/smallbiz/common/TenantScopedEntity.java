package com.smallbiz.common;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * 属于某个商户的业务数据的基类。
 *
 * <p>{@link TenantId} 让 Hibernate 自动完成多商户隔离：
 * <ul>
 *   <li>查询时自动追加 {@code tenant_id = 当前商户}；</li>
 *   <li>新增时自动写入当前商户 id（忽略调用方设置的值）。</li>
 * </ul>
 * 当前商户由 {@link com.smallbiz.tenant.TenantIdentifierResolver} 从登录令牌中解析。
 * 注意：原生 SQL 不受此机制保护，必须手写 tenant_id 条件。
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }
}
