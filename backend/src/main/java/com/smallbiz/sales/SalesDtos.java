package com.smallbiz.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.customer.Customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SalesDtos {

    private SalesDtos() {
    }

    /** unitPrice 为空时按价格类型取商品零售价 / 批发价。 */
    public record ItemRequest(
            @NotNull(message = "请选择商品") Long productId,
            @Min(value = 1, message = "数量至少为 1") @Max(value = 1_000_000, message = "数量过大") int quantity,
            @DecimalMin(value = "0", message = "单价不能为负数")
            @Digits(integer = 12, fraction = 2, message = "单价最多两位小数") BigDecimal unitPrice) {
    }

    public record SaleRequest(
            Long customerId,
            @NotNull(message = "请选择价格类型") Customer.PriceTier priceTier,
            @DecimalMin(value = "0", message = "优惠不能为负数")
            @Digits(integer = 12, fraction = 2, message = "优惠最多两位小数") BigDecimal discountAmount,
            @Size(max = 500, message = "备注最多 500 字") String notes,
            /** 本次实收；为空表示全额收款。小于应收的部分记为客户欠款（专业版，需选择客户）。 */
            @DecimalMin(value = "0", message = "实收不能为负数")
            @Digits(integer = 12, fraction = 2, message = "实收最多两位小数") BigDecimal paidAmount,
            @NotEmpty(message = "请至少添加一个商品") @Size(max = 200, message = "一张销售单最多 200 个商品")
            List<@Valid ItemRequest> items) {
    }

    public record CancelRequest(@Size(max = 200, message = "原因最多 200 字") String reason) {
    }

    /** 店员看不到成本：unitCost 为 null。 */
    public record ItemView(
            Long productId,
            String productName,
            String sku,
            String unit,
            int quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lineTotal,
            PriceSource priceSource) {
    }

    public record Creator(Long id, String name) {
    }

    public record SaleSummary(
            Long id,
            String orderNo,
            Long customerId,
            String customerName,
            SalesOrder.Status status,
            Instant orderDate,
            int itemCount,
            int totalQuantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
            SalesOrder.PaymentStatus paymentStatus,
            Long createdBy,
            String createdByName) {

        static SaleSummary of(SalesOrder o, String createdByName) {
            return new SaleSummary(o.getId(), o.getOrderNo(), o.getCustomerId(), o.getCustomerName(), o.getStatus(),
                    o.getOrderDate(), o.getItemCount(), o.getTotalQuantity(), o.getTotalAmount(), o.getPaidAmount(),
                    o.getPaymentStatus(), o.getCreatedBy(), createdByName);
        }
    }

    /** 详情。店员看不到 totalCost / grossProfit（为 null）。 */
    public record SaleView(
            Long id,
            String orderNo,
            Long customerId,
            String customerName,
            Customer.PriceTier priceTier,
            SalesOrder.Status status,
            Instant orderDate,
            int itemCount,
            int totalQuantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
            SalesOrder.PaymentStatus paymentStatus,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAtSale,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstanding,
            Instant lastPaymentAt,
            List<PaymentLine> payments,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grossProfit,
            String notes,
            List<ItemView> items,
            String createdByName,
            Instant cancelledAt,
            String cancelledByName,
            String cancelReason) {
    }

    /** 冲抵到这张销售单的收款（含已作废的，便于追溯）。 */
    public record PaymentLine(Long paymentId, String paymentNo, Instant receivedAt, String method, String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record CustomerStats(long orderCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalSpent, Instant lastOrderAt) {
    }
}
