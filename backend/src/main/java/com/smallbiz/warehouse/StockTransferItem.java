package com.smallbiz.warehouse;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productName;

    @Column(name = "sku_snapshot", nullable = false, length = 64)
    private String sku;

    @Column(name = "unit_snapshot", nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    protected StockTransferItem() {
    }

    public StockTransferItem(Long productId, String productName, String sku, String unit, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.unit = unit;
        this.quantity = quantity;
    }

    void attach(StockTransfer transfer, int lineNo) {
        this.transfer = transfer;
        this.lineNo = lineNo;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public String getUnit() {
        return unit;
    }

    public int getQuantity() {
        return quantity;
    }
}
