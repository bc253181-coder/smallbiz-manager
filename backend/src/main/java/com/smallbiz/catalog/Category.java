package com.smallbiz.catalog;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 商品分类（最多两级）。 */
@Entity
@Table(name = "categories")
public class Category extends TenantScopedEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
    }

    public Category(Long parentId, String name, int sortOrder) {
        this.parentId = parentId;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
