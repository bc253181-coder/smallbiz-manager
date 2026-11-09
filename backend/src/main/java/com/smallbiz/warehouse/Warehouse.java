package com.smallbiz.warehouse;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 仓库 / 门店。每家店有且只有一个默认仓库（不能停用）。 */
@Entity
@Table(name = "warehouses")
public class Warehouse extends TenantScopedEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 500)
    private String notes;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    private int version;

    protected Warehouse() {
    }

    public Warehouse(String name, boolean isDefault, int sortOrder) {
        this.name = name;
        this.isDefault = isDefault;
        this.sortOrder = sortOrder;
    }

    public void update(String name, String address, String notes) {
        this.name = name;
        this.address = address;
        this.notes = notes;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getVersion() {
        return version;
    }
}
