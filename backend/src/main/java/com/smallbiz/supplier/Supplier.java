package com.smallbiz.supplier;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 供应商。软删除，历史采购单仍可引用。 */
@Entity
@Table(name = "suppliers")
@SQLRestriction("deleted_at is null")
public class Supplier extends TenantScopedEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    @Column(length = 30)
    private String phone;

    @Column(length = 191)
    private String email;

    @Column(length = 300)
    private String address;

    /** 供应商品的文字说明；实际供货关系由采购单体现。 */
    @Column(name = "supplied_products_note", length = 500)
    private String suppliedProductsNote;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    private int version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected Supplier() {
    }

    public Supplier(Long createdBy) {
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

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
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

    public String getSuppliedProductsNote() {
        return suppliedProductsNote;
    }

    public void setSuppliedProductsNote(String suppliedProductsNote) {
        this.suppliedProductsNote = suppliedProductsNote;
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
