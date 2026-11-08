package com.smallbiz.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record ItemRequest(
            @NotNull(message = "请选择商品") Long productId,
            @Min(value = 1, message = "数量至少为 1") @Max(value = 1_000_000, message = "数量过大") int quantity,
            @NotNull(message = "请填写采购单价") @DecimalMin(value = "0", message = "单价不能为负数")
            @Digits(integer = 12, fraction = 2, message = "单价最多两位小数") BigDecimal unitCost) {
    }

    /** 新增和修改共用。submit=true 时保存后直接下单（新增时有效）；修改时必须带 version。 */
    public record PurchaseRequest(
            @NotNull(message = "请选择供应商") Long supplierId,
            LocalDate expectedDate,
            @Size(max = 500, message = "备注最多 500 字") String notes,
            @NotEmpty(message = "请至少添加一个商品") @Size(max = 200, message = "一张采购单最多 200 个商品")
            List<@Valid ItemRequest> items,
            boolean submit,
            Integer version) {
    }

    public record CancelRequest(@Size(max = 200, message = "原因最多 200 字") String reason) {
    }

    public record ItemView(
            Long id,
            Long productId,
            String productName,
            String sku,
            String unit,
            int quantity,
            int receivedQty,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lineTotal) {
    }

    /** 列表行。 */
    public record PurchaseSummary(
            Long id,
            String orderNo,
            Long supplierId,
            String supplierName,
            PurchaseStatus status,
            Instant orderDate,
            LocalDate expectedDate,
            int itemCount,
            int totalQuantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount) {

        static PurchaseSummary of(PurchaseOrder o) {
            return new PurchaseSummary(o.getId(), o.getOrderNo(), o.getSupplierId(), o.getSupplierName(),
                    o.getStatus(), o.getOrderDate(), o.getExpectedDate(), o.getItemCount(), o.getTotalQuantity(),
                    o.getTotalAmount());
        }
    }

    /** 详情。 */
    public record PurchaseView(
            Long id,
            String orderNo,
            Long supplierId,
            String supplierName,
            PurchaseStatus status,
            Instant orderDate,
            LocalDate expectedDate,
            int itemCount,
            int totalQuantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
            String notes,
            List<ItemView> items,
            String createdByName,
            Instant submittedAt,
            Instant receivedAt,
            String receivedByName,
            Instant cancelledAt,
            String cancelledByName,
            String cancelReason,
            int version) {
    }

    /** 供应商供应过的商品。 */
    public record SuppliedProduct(Long productId, String productName, String sku, String unit, long totalQuantity,
            Instant lastReceivedAt, long orderCount) {
    }
}
