package com.smallbiz.warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record WarehouseRequest(
            @NotBlank(message = "请填写仓库名称") @Size(max = 50, message = "仓库名称最多 50 字") String name,
            @Size(max = 300, message = "地址最多 300 字") String address,
            @Size(max = 500, message = "备注最多 500 字") String notes) {
    }

    /**
     * usable：当前套餐下能否在这个仓库开单 / 入库 / 盘点（专业版到期后只有默认仓库可用，其他仓库只能调出）。
     * stockValue：店员为 null。
     */
    public record WarehouseView(Long id, String name, String address, String notes, boolean isDefault, boolean active,
            boolean usable, long productCount, long totalQty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal stockValue, int version) {
    }

    /** max：当前套餐的仓库数量上限；allowed：能否新增仓库。 */
    public record WarehouseList(List<WarehouseView> warehouses, int max, boolean allowed) {
    }

    public record StockLine(Long warehouseId, String warehouseName, int qty) {
    }

    public record TransferItemRequest(
            @NotNull(message = "请选择商品") Long productId,
            @Min(value = 1, message = "数量至少为 1") @Max(value = 1_000_000_000, message = "数量过大") int quantity) {
    }

    public record TransferRequest(
            @NotNull(message = "请选择调出仓库") Long fromWarehouseId,
            @NotNull(message = "请选择调入仓库") Long toWarehouseId,
            @NotEmpty(message = "请至少添加一个商品") @Size(max = 200, message = "一张调拨单最多 200 个商品") List<@Valid TransferItemRequest> items,
            @Size(max = 500, message = "备注最多 500 字") String notes) {
    }

    public record TransferItemView(Long productId, String productName, String sku, String unit, int quantity) {
    }

    public record TransferView(Long id, String transferNo, Long fromWarehouseId, String fromWarehouseName,
            Long toWarehouseId, String toWarehouseName, String notes, int itemCount, int totalQuantity,
            String createdByName, Instant createdAt, List<TransferItemView> items) {
    }
}
