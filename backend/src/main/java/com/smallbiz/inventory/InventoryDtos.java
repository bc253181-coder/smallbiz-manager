package com.smallbiz.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record StockInRequest(
            @NotNull(message = "请选择商品") Long productId,
            @Min(value = 1, message = "数量至少为 1") @Max(value = 1_000_000_000, message = "数量过大") int quantity,
            @DecimalMin(value = "0", message = "单价不能为负数")
            @Digits(integer = 12, fraction = 2, message = "单价最多两位小数") BigDecimal unitCost,
            @Size(max = 200, message = "备注最多 200 字") String reason,
            Long warehouseId) {
    }

    public record StockOutRequest(
            @NotNull(message = "请选择商品") Long productId,
            @Min(value = 1, message = "数量至少为 1") @Max(value = 1_000_000_000, message = "数量过大") int quantity,
            @NotBlank(message = "请填写出库原因") @Size(max = 200, message = "原因最多 200 字") String reason,
            Long warehouseId) {
    }

    public record AdjustRequest(
            @NotNull(message = "请选择商品") Long productId,
            @NotNull(message = "请填写实际数量") @Min(value = 0, message = "实际数量不能为负数")
            @Max(value = 1_000_000_000, message = "数量过大") Integer actualQty,
            @NotBlank(message = "请填写调整原因") @Size(max = 200, message = "原因最多 200 字") String reason,
            Long warehouseId) {
    }

    /** 库存操作结果：本次流水 + 商品最新库存。 */
    /** stockQty 为商品总库存，warehouseQty 为本次操作仓库的库存。 */
    public record StockResult(Long movementId, Long productId, int qtyChange, int stockQty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costPrice, Long warehouseId, int warehouseQty) {
    }

    /** 流水列表行（带商品名称，用于全店流水页）。 */
    public record MovementRow(
            Long id,
            Long productId,
            String productName,
            String productSku,
            String productUnit,
            Long warehouseId,
            String warehouseName,
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

    /** 库存汇总。店员看不到库存价值（inventoryValue 为 null）。 */
    public record Summary(
            long productCount,
            long totalQty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inventoryValue,
            long lowStockCount,
            long outOfStockCount) {
    }

    public record LowStockItem(Long id, String sku, String name, String unit, String imageUrl, int stockQty,
            int lowStockThreshold) {
    }

    public record LowStockList(long count, List<LowStockItem> items) {
    }
}
