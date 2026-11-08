package com.smallbiz.purchasing;

import java.math.BigDecimal;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder order;

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

    @Column(name = "received_qty", nullable = false)
    private int receivedQty;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(Long productId, String productName, String sku, String unit, int quantity,
            BigDecimal unitCost) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.unit = unit;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.lineTotal = unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    void attach(PurchaseOrder order, int lineNo) {
        this.order = order;
        this.lineNo = lineNo;
    }

    void markFullyReceived() {
        this.receivedQty = quantity;
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

    public int getReceivedQty() {
        return receivedQty;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public int getLineNo() {
        return lineNo;
    }
}
