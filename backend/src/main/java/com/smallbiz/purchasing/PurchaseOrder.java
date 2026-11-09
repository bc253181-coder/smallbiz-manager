package com.smallbiz.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.smallbiz.common.TenantScopedEntity;

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
@Table(name = "purchase_orders")
public class PurchaseOrder extends TenantScopedEntity {

    @Column(name = "order_no", nullable = false, length = 30, updatable = false)
    private String orderNo;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_name_snapshot", nullable = false, length = 100)
    private String supplierName;

    /** 收货仓库（收货时确定）。 */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(length = 500)
    private String notes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by")
    private Long receivedBy;

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
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String orderNo, Instant orderDate, Long createdBy) {
        this.orderNo = orderNo;
        this.orderDate = orderDate;
        this.createdBy = createdBy;
        this.status = PurchaseStatus.DRAFT;
    }

    /** 替换全部明细并重新计算合计。 */
    public void replaceItems(List<PurchaseOrderItem> newItems) {
        items.clear();
        int line = 1;
        BigDecimal total = BigDecimal.ZERO;
        int qty = 0;
        for (PurchaseOrderItem item : newItems) {
            item.attach(this, line++);
            items.add(item);
            total = total.add(item.getLineTotal());
            qty += item.getQuantity();
        }
        this.totalAmount = total;
        this.itemCount = newItems.size();
        this.totalQuantity = qty;
    }

    public void submit(Instant now) {
        this.status = PurchaseStatus.ORDERED;
        this.submittedAt = now;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void markReceived(Instant now, Long userId) {
        this.status = PurchaseStatus.RECEIVED;
        this.receivedAt = now;
        this.receivedBy = userId;
        items.forEach(PurchaseOrderItem::markFullyReceived);
    }

    public void cancel(Instant now, Long userId, String reason) {
        this.status = PurchaseStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = userId;
        this.cancelReason = reason;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplier(Long supplierId, String supplierName) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getReceivedBy() {
        return receivedBy;
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

    public int getVersion() {
        return version;
    }

    public List<PurchaseOrderItem> getItems() {
        return items;
    }
}
