package com.smallbiz.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sales_order_items")
public class SalesOrderItem extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder order;

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

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "price_source", nullable = false, length = 20)
    private PriceSource priceSource;

    /** 销售时的成本快照。 */
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    protected SalesOrderItem() {
    }

    public SalesOrderItem(Long productId, String productName, String sku, String unit, int quantity,
            BigDecimal unitPrice, PriceSource priceSource, BigDecimal unitCost) {
        this.priceSource = priceSource;
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.unit = unit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    void attach(SalesOrder order, int lineNo) {
        this.order = order;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
