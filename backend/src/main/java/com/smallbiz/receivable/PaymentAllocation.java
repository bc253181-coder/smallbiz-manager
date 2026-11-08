package com.smallbiz.receivable;

import java.math.BigDecimal;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 一笔收款冲抵到某张销售单的金额。收款作废时保留记录（按收款状态判断是否有效）。 */
@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation extends TenantScopedEntity {

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "sales_order_id", nullable = false, updatable = false)
    private Long salesOrderId;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(Long paymentId, Long salesOrderId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.salesOrderId = salesOrderId;
        this.amount = amount;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}