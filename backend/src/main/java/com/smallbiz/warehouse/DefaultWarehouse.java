package com.smallbiz.warehouse;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.TenantRepository;

/**
 * 每家店的默认仓库（「主仓库」）。开店时创建；万一缺失（例如迁移前的边界情况）在第一次使用时补建，
 * 补建前锁住店铺行，避免并发时建出两个（数据库也有唯一约束兜底）。
 */
@Component
public class DefaultWarehouse {

    public static final String DEFAULT_NAME = "主仓库";

    private final WarehouseRepository warehouses;
    private final TenantRepository tenants;

    public DefaultWarehouse(WarehouseRepository warehouses, TenantRepository tenants) {
        this.warehouses = warehouses;
        this.tenants = tenants;
    }

    /** 当前店铺（由登录令牌或 TenantContext 决定）的默认仓库 id。 */
    @Transactional
    public Long id(Long tenantId) {
        return warehouses.findDefault().map(Warehouse::getId).orElseGet(() -> {
            tenants.findByIdForUpdate(tenantId).orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
            return warehouses.findDefault()
                    .orElseGet(() -> warehouses.saveAndFlush(new Warehouse(DEFAULT_NAME, true, 0)))
                    .getId();
        });
    }
}
