package com.smallbiz.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** 仪表盘。店员看不到毛利和采购额（为 null）。 */
    public record DashboardSummary(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal todaySales,
            long todayOrders,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthSales,
            long monthOrders,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthGrossProfit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthPurchases,
            LocalDate today) {
    }

    /** 一个时间段（某天 / 某月）的销售汇总。period 为 2026-11-09 或 2026-11。 */
    public record SalesPoint(
            String period,
            long orderCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cost,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grossProfit) {
    }

    public record SalesReport(LocalDate from, LocalDate to, String groupBy, List<SalesPoint> points,
            SalesPoint total) {
    }

    public record PurchasePoint(String period, long orderCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record PurchaseReport(LocalDate from, LocalDate to, String groupBy, List<PurchasePoint> points,
            PurchasePoint total) {
    }

    public record ProductRank(
            Long productId,
            String productName,
            String sku,
            String unit,
            long quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grossProfit) {
    }

    public record CustomerRank(Long customerId, String customerName, long orderCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record CategoryStock(Long categoryId, String categoryName, long productCount, long quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value) {
    }
}
