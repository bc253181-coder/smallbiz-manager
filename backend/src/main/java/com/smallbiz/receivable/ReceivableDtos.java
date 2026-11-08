package com.smallbiz.receivable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.sales.SalesOrder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReceivableDtos {

    private ReceivableDtos() {
    }

    /** salesOrderId 不为空时只冲抵这张销售单；否则按开单时间先欠先还。 */
    public record PaymentRequest(
            @NotNull(message = "请选择客户") Long customerId,
            @NotNull(message = "请填写收款金额") @DecimalMin(value = "0.01", message = "收款金额必须大于 0")
            @Digits(integer = 12, fraction = 2, message = "金额最多两位小数") BigDecimal amount,
            @NotNull(message = "请选择收款方式") CustomerPayment.Method method,
            Instant receivedAt,
            @Size(max = 500, message = "备注最多 500 字") String notes,
            Long salesOrderId) {
    }

    public record VoidRequest(@NotBlank(message = "请填写作废原因") @Size(max = 200, message = "原因最多 200 字") String reason) {
    }

    public record AllocationView(Long salesOrderId, String orderNo, Instant orderDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record PaymentView(
            Long id,
            String paymentNo,
            Long customerId,
            String customerName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            CustomerPayment.Method method,
            Instant receivedAt,
            String notes,
            CustomerPayment.Status status,
            Instant voidedAt,
            String voidedByName,
            String voidReason,
            String createdByName,
            Instant createdAt,
            List<AllocationView> allocations) {
    }

    /** 账龄分布：按每张单的未收金额和开单天数归类。 */
    public record Aging(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal days0To30,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal days31To60,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal days61To90,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal over90) {
    }

    public record Summary(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalOutstanding,
            long customerCount,
            long openOrderCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdue30,
            Aging aging,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal collectedThisMonth) {
    }

    public record CustomerRow(
            Long customerId,
            String customerName,
            String phone,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstanding,
            int openOrderCount,
            Instant oldestOrderDate,
            long oldestDays,
            Aging aging) {
    }

    public record OpenOrder(
            Long id,
            String orderNo,
            Instant orderDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstanding,
            SalesOrder.PaymentStatus paymentStatus,
            long ageDays) {
    }

    public record CustomerReceivable(
            Long customerId,
            String customerName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstanding,
            Aging aging,
            List<OpenOrder> orders) {
    }

    public enum LineType { SALE, PAYMENT }

    /**
     * 对账单一行。SALE：amount = 应收，paidAtSale = 开单时实收，debit = 新增欠款；
     * PAYMENT：credit = 收款金额。balance 为该行之后的欠款余额。
     */
    public record StatementLine(
            Instant date,
            LineType type,
            Long refId,
            String docNo,
            String description,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paidAtSale,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal debit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal credit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {
    }

    public record Statement(
            Long customerId,
            String customerName,
            String customerPhone,
            String storeName,
            String storePhone,
            String storeAddress,
            String currency,
            LocalDate from,
            LocalDate to,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingBalance,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalSales,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDebit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCredit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closingBalance,
            List<StatementLine> lines,
            Instant generatedAt) {
    }
}
