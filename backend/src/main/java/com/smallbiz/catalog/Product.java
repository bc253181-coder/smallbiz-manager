package com.smallbiz.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.SQLRestriction;

import com.smallbiz.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 商品。已删除（软删除）的商品对所有查询不可见。
 * 库存数量 {@code stockQty} 只能通过 {@link com.smallbiz.inventory.InventoryService} 修改。
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at is null")
public class Product extends TenantScopedEntity {

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 相对于上传目录的路径，例如 t12/3f2a….jpg */
    @Column(name = "image_path", length = 300)
    private String imagePath;

    @Column(nullable = false, length = 20)
    private String unit = "件";

    @Column(length = 64)
    private String barcode;

    @Column(name = "cost_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "retail_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal retailPrice = BigDecimal.ZERO;

    @Column(name = "wholesale_price", precision = 14, scale = 2)
    private BigDecimal wholesalePrice;

    /** 库存变化不应让正在编辑商品资料的人遇到"数据已被修改"冲突，因此不参与乐观锁。 */
    @OptimisticLock(excluded = true)
    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Version
    private int version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected Product() {
    }

    public Product(String sku, String name, Long createdBy) {
        this.sku = sku;
        this.name = name;
        this.createdBy = createdBy;
    }

    public boolean isLowStock() {
        return stockQty <= lowStockThreshold;
    }

    /** 软删除：同时改写 SKU，释放原 SKU 供新商品使用。 */
    public void softDelete(Instant now) {
        String suffix = "#d" + now.getEpochSecond();
        String base = sku.length() + suffix.length() > 64 ? sku.substring(0, 64 - suffix.length()) : sku;
        this.sku = base + suffix;
        this.deletedAt = now;
    }

    /** 仅供 InventoryService 调用。 */
    public void applyStockChange(int newQty) {
        this.stockQty = newQty;
    }

    /** 仅供 InventoryService 调用：用加锁读到的最新库存和成本覆盖可能过期的字段。 */
    public void syncFromLockedRead(int currentStock, BigDecimal currentCost) {
        this.stockQty = currentStock;
        this.costPrice = currentCost;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public BigDecimal getWholesalePrice() {
        return wholesalePrice;
    }

    public void setWholesalePrice(BigDecimal wholesalePrice) {
        this.wholesalePrice = wholesalePrice;
    }

    public int getStockQty() {
        return stockQty;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }
}
