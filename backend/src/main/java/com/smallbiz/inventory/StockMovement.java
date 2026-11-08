package com.smallbiz.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 库存流水：只增不改。 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "ref_type", length = 20)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    StockMovement(Long productId, MovementType type, int qtyChange, int qtyBefore, BigDecimal unitCost,
            String refType, Long refId, String reason, Long createdBy) {
        this.productId = productId;
        this.type = type;
        this.qtyChange = qtyChange;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyBefore + qtyChange;
        this.unitCost = unitCost;
        this.refType = refType;
        this.refId = refId;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getProductId() {
        return productId;
    }

    public MovementType getType() {
        return type;
    }

    public int getQtyChange() {
        return qtyChange;
    }

    public int getQtyBefore() {
        return qtyBefore;
    }

    public int getQtyAfter() {
        return qtyAfter;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getReason() {
        return reason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
