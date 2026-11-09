package com.smallbiz.warehouse;

import java.util.ArrayList;
import java.util.List;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** 调拨单：保存即生效，不能修改；调错了做一张反向调拨。 */
@Entity
@Table(name = "stock_transfers")
public class StockTransfer extends TenantScopedEntity {

    @Column(name = "transfer_no", nullable = false, length = 30, updatable = false)
    private String transferNo;

    @Column(name = "from_warehouse_id", nullable = false, updatable = false)
    private Long fromWarehouseId;

    @Column(name = "to_warehouse_id", nullable = false, updatable = false)
    private Long toWarehouseId;

    @Column(length = 500)
    private String notes;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo asc")
    private List<StockTransferItem> items = new ArrayList<>();

    protected StockTransfer() {
    }

    public StockTransfer(String transferNo, Long fromWarehouseId, Long toWarehouseId, String notes, Long createdBy) {
        this.transferNo = transferNo;
        this.fromWarehouseId = fromWarehouseId;
        this.toWarehouseId = toWarehouseId;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public void setItems(List<StockTransferItem> list) {
        items.clear();
        int line = 1;
        for (StockTransferItem i : list) {
            i.attach(this, line++);
            items.add(i);
        }
        itemCount = items.size();
        totalQuantity = items.stream().mapToInt(StockTransferItem::getQuantity).sum();
    }

    public String getTransferNo() {
        return transferNo;
    }

    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public String getNotes() {
        return notes;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public List<StockTransferItem> getItems() {
        return items;
    }
}
