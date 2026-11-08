package com.smallbiz.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.smallbiz.common.TenantScopedEntity;
import com.smallbiz.customer.Customer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "sales_orders")
public class SalesOrder extends TenantScopedEntity {

    public enum Status { COMPLETED, CANCELLED }

    public enum PaymentStatus { PAID, PARTIAL, UNPAID }

    @Column(name = "order_no", nullable = false, length = 30, updatable = false)
    private String orderNo;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name_snapshot", length = 100)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_tier", nullable = false, length = 20)
    private Customer.PriceTier priceTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private int version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<SalesOrderItem> items = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(String orderNo, Instant orderDate, Customer.PriceTier priceTier, Long createdBy) {
        this.orderNo = orderNo;
        this.orderDate = orderDate;
        this.priceTier = priceTier;
        this.createdBy = createdBy;
    }

    /** 设置明细并计算金额：应收 = 明细合计 − 整单优惠；MVP 阶段视为已全额收款。 */
    public void setItems(List<SalesOrderItem> newItems, BigDecimal discount) {
        items.clear();
        int line = 1;
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        int qty = 0;
        for (SalesOrderItem item : newItems) {
            item.attach(this, line++);
            items.add(item);
            sub = sub.add(item.getLineTotal());
            cost = cost.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
            qty += item.getQuantity();
        }
        this.subtotal = sub;
        this.discountAmount = discount;
        this.totalAmount = sub.subtract(discount);
        this.totalCost = cost.setScale(2, java.math.RoundingMode.HALF_UP);
        this.paidAmount = totalAmount;
        this.paymentStatus = PaymentStatus.PAID;
        this.itemCount = newItems.size();
        this.totalQuantity = qty;
    }

    public void cancel(Instant now, Long userId, String reason) {
        this.status = Status.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = userId;
        this.cancelReason = reason;
    }

    /** 毛利 = 实收 − 成本。 */
    public BigDecimal grossProfit() {
        return totalAmount.subtract(totalCost);
    }

    public void setCustomer(Long customerId, String customerName) {
        this.customerId = customerId;
        this.customerName = customerName;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Customer.PriceTier getPriceTier() {
        return priceTier;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public List<SalesOrderItem> getItems() {
        return items;
    }
}
