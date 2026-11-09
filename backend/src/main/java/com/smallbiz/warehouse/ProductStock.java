package com.smallbiz.warehouse;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 商品在某个仓库的库存（只读映射）。写入只通过 InventoryService 的加锁 SQL 完成，
 * 保证 products.stock_qty = 各仓库之和。
 */
@Entity
@Table(name = "product_stocks")
@org.hibernate.annotations.Immutable
public class ProductStock extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private int qty;

    protected ProductStock() {
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public int getQty() {
        return qty;
    }
}
