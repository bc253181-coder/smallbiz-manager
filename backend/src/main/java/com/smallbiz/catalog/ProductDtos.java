package com.smallbiz.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.inventory.MovementType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {
    }

    static final String SKU_PATTERN = "^[A-Za-z0-9._\\-/]*$";
    static final String SKU_MESSAGE = "SKU 只能包含字母、数字和 . _ - /";

    public record CreateProductRequest(
            @NotBlank(message = "请填写商品名称") @Size(max = 200, message = "商品名称最多 200 字") String name,
            @Size(max = 64, message = "SKU 最多 64 位") @Pattern(regexp = SKU_PATTERN, message = SKU_MESSAGE) String sku,
            Long categoryId,
            @Size(max = 5000, message = "描述最多 5000 字") String description,
            @NotBlank(message = "请填写单位") @Size(max = 20, message = "单位最多 20 字") String unit,
            @Size(max = 64, message = "条码最多 64 位") String barcode,
            @NotNull(message = "请填写进货成本") @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal costPrice,
            @NotNull(message = "请填写零售价") @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal retailPrice,
            @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal wholesalePrice,
            @Min(value = 0, message = "预警值不能为负数") @Max(value = 1_000_000_000, message = "预警值过大") int lowStockThreshold,
            ProductStatus status,
            @Min(value = 0, message = "初始库存不能为负数") @Max(value = 1_000_000_000, message = "初始库存过大") int initialStock) {
    }

    public record UpdateProductRequest(
            @NotBlank(message = "请填写商品名称") @Size(max = 200, message = "商品名称最多 200 字") String name,
            @NotBlank(message = "请填写 SKU") @Size(max = 64, message = "SKU 最多 64 位")
            @Pattern(regexp = SKU_PATTERN, message = SKU_MESSAGE) String sku,
            Long categoryId,
            @Size(max = 5000, message = "描述最多 5000 字") String description,
            @NotBlank(message = "请填写单位") @Size(max = 20, message = "单位最多 20 字") String unit,
            @Size(max = 64, message = "条码最多 64 位") String barcode,
            @NotNull(message = "请填写进货成本") @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal costPrice,
            @NotNull(message = "请填写零售价") @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal retailPrice,
            @DecimalMin(value = "0", message = "价格不能为负数")
            @Digits(integer = 12, fraction = 2, message = "价格最多两位小数") BigDecimal wholesalePrice,
            @Min(value = 0, message = "预警值不能为负数") @Max(value = 1_000_000_000, message = "预警值过大") int lowStockThreshold,
            @NotNull(message = "请选择状态") ProductStatus status,
            @NotNull(message = "缺少版本号") Integer version) {
    }

    /**
     * 商品信息。金额以字符串输出（如 "12.50"），避免前端浮点误差。
     * 店员（STAFF）看不到进货成本：costPrice 为 null。
     */
    public record ProductView(
            Long id,
            String sku,
            String name,
            String description,
            String imageUrl,
            String unit,
            String barcode,
            Long categoryId,
            String categoryName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal retailPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal wholesalePrice,
            int stockQty,
            int lowStockThreshold,
            boolean lowStock,
            ProductStatus status,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MovementView(
            Long id,
            MovementType type,
            int qtyChange,
            int qtyBefore,
            int qtyAfter,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitCost,
            String refType,
            Long refId,
            String reason,
            String operatorName,
            Instant createdAt) {
    }
}
