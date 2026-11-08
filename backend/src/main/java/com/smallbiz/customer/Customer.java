package com.smallbiz.customer;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 客户。已删除（软删除）的客户对所有查询不可见，历史订单保存客户名称快照。 */
@Entity
@Table(name = "customers")
@SQLRestriction("deleted_at is null")
public class Customer extends TenantScopedEntity {

    public enum Type { INDIVIDUAL, BUSINESS }

    /** 开单时默认使用的价格。 */
    public enum PriceTier { RETAIL, WHOLESALE }

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 191)
    private String email;

    @Column(length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private Type customerType = Type.INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_tier", nullable = false, length = 20)
    private PriceTier priceTier = PriceTier.RETAIL;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    private int version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected Customer() {
    }

    public Customer(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Type getCustomerType() {
        return customerType;
    }

    public void setCustomerType(Type customerType) {
        this.customerType = customerType;
    }

    public PriceTier getPriceTier() {
        return priceTier;
    }

    public void setPriceTier(PriceTier priceTier) {
        this.priceTier = priceTier;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getVersion() {
        return version;
    }
}
