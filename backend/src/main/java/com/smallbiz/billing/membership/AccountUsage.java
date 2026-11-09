package com.smallbiz.billing.membership;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 普通会员按账号累计的用量（删除不返还）。 */
@Entity
@Table(name = "account_usage")
public class AccountUsage {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "stores_created", nullable = false)
    private int storesCreated;

    @Column(name = "products_created", nullable = false)
    private int productsCreated;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountUsage() {
    }

    public AccountUsage(Long userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public void addStores(int n) {
        storesCreated += n;
        updatedAt = Instant.now();
    }

    public void addProducts(int n) {
        productsCreated += n;
        updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public int getStoresCreated() {
        return storesCreated;
    }

    public int getProductsCreated() {
        return productsCreated;
    }
}
