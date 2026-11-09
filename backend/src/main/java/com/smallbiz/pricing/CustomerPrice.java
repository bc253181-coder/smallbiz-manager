package com.smallbiz.pricing;

import java.math.BigDecimal;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 客户协议价：一个客户 + 一个商品只有一条。 */
@Entity
@Table(name = "customer_prices")
public class CustomerPrice extends TenantScopedEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(length = 200)
    private String notes;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private int version;

    protected CustomerPrice() {
    }

    public CustomerPrice(Long customerId, Long productId, Long userId) {
        this.customerId = customerId;
        this.productId = productId;
        this.createdBy = userId;
    }

    public void set(BigDecimal price, String notes, Long userId) {
        this.price = price;
        this.notes = notes;
        this.updatedBy = userId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getNotes() {
        return notes;
    }
}
