package com.smallbiz.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.excel.XlsxWriter;
import com.smallbiz.excel.XlsxWriter.Column;
import com.smallbiz.report.ReportDtos.CategoryStock;
import com.smallbiz.report.ReportDtos.CustomerRank;
import com.smallbiz.report.ReportDtos.DashboardSummary;
import com.smallbiz.report.ReportDtos.ProductRank;
import com.smallbiz.report.ReportDtos.PurchaseReport;
import com.smallbiz.report.ReportDtos.SalesReport;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;

/**
 * 仪表盘（所有成员，店员看不到毛利和采购额）与经营报表（仅店主/管理员）。
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService service;
    private final com.smallbiz.billing.PlanGuard planGuard;

    public ReportController(ReportService service, com.smallbiz.billing.PlanGuard planGuard) {
        this.planGuard = planGuard;
        this.service = service;
    }

    private static boolean hideCost() {
        return CurrentUser.roleOrNull() == MemberRole.STAFF;
    }

    // ---------------- 仪表盘 ----------------

    @GetMapping("/dashboard/summary")
    public DashboardSummary dashboard() {
        return service.dashboard(CurrentUser.requireTenantId(), hideCost());
    }

    @GetMapping("/dashboard/sales-trend")
    public SalesReport salesTrend(@RequestParam(defaultValue = "30") int days) {
        return service.salesTrend(CurrentUser.requireTenantId(), days, hideCost());
    }

    // ---------------- 报表 ----------------

    @GetMapping("/reports/sales")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SalesReport sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportService.GroupBy groupBy) {
        return service.sales(CurrentUser.requireTenantId(), from, to, groupBy, false);
    }

    @GetMapping("/reports/purchases")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PurchaseReport purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportService.GroupBy groupBy) {
        return service.purchases(CurrentUser.requireTenantId(), from, to, groupBy);
    }

    @GetMapping("/reports/top-products")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<ProductRank> topProducts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "amount") String by, @RequestParam(defaultValue = "10") int limit) {
        return service.topProducts(CurrentUser.requireTenantId(), from, to, by, limit);
    }

    @GetMapping("/reports/top-customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<CustomerRank> topCustomers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return service.topCustomers(CurrentUser.requireTenantId(), from, to, limit);
    }

    @GetMapping("/reports/inventory")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<CategoryStock> inventory() {
        CurrentUser.requireTenantId();
        return service.inventoryByCategory();
    }

    // ---------------- 导出 CSV ----------------

    @GetMapping("/reports/sales/export")
    @com.smallbiz.billing.RequiresFeature(com.smallbiz.billing.Feature.EXPORT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> exportSales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportService.GroupBy groupBy,
            @RequestParam(defaultValue = "csv") String format) {
        SalesReport r = service.sales(CurrentUser.requireTenantId(), from, to, groupBy, false);
        List<List<Object>> rows = new java.util.ArrayList<>(r.points().stream()
                .map(p -> List.<Object>of(p.period(), p.orderCount(), p.amount(), p.cost(), p.grossProfit())).toList());
        var t = r.total();
        rows.add(List.of(t.period(), t.orderCount(), t.amount(), t.cost(), t.grossProfit()));
        String period = r.groupBy().equals("MONTH") ? "月份" : "日期";
        if (xlsx(format)) {
            return new XlsxWriter(ZoneId.of("UTC")).sheet("销售报表", List.of(Column.text(period), Column.integer("订单数"),
                    Column.money("销售额"), Column.money("成本"), Column.money("毛利")), rows)
                    .response("销售报表_" + r.from() + "_" + r.to() + ".xlsx");
        }
        return CsvWriter.response("销售报表_" + r.from() + "_" + r.to() + ".csv",
                List.of(period, "订单数", "销售额", "成本", "毛利"), rows);
    }

    @GetMapping("/reports/top-products/export")
    @com.smallbiz.billing.RequiresFeature(com.smallbiz.billing.Feature.EXPORT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> exportProducts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "amount") String by,
            @RequestParam(defaultValue = "csv") String format) {
        List<ProductRank> list = service.topProducts(CurrentUser.requireTenantId(), from, to, by, 100);
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ProductRank p = list.get(i);
            rows.add(List.of(i + 1, p.productName(), p.sku(), p.quantity(), p.unit(), p.amount(), p.grossProfit()));
        }
        if (xlsx(format)) {
            return new XlsxWriter(ZoneId.of("UTC")).sheet("商品销售排行", List.of(Column.integer("排名"), Column.text("商品"),
                    Column.text("SKU"), Column.integer("销量"), Column.text("单位"), Column.money("销售额"),
                    Column.money("毛利")), rows).response("商品销售排行.xlsx");
        }
        return CsvWriter.response("商品销售排行.csv", List.of("排名", "商品", "SKU", "销量", "单位", "销售额", "毛利"), rows);
    }

    @GetMapping("/reports/top-customers/export")
    @com.smallbiz.billing.RequiresFeature(com.smallbiz.billing.Feature.EXPORT)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> exportCustomers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format) {
        List<CustomerRank> list = service.topCustomers(CurrentUser.requireTenantId(), from, to, 100);
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CustomerRank c = list.get(i);
            rows.add(List.of(i + 1, c.customerName(), c.orderCount(), c.amount()));
        }
        if (xlsx(format)) {
            return new XlsxWriter(ZoneId.of("UTC")).sheet("客户消费排行", List.of(Column.integer("排名"), Column.text("客户"),
                    Column.integer("订单数"), Column.money("消费金额")), rows).response("客户消费排行.xlsx");
        }
        return CsvWriter.response("客户消费排行.csv", List.of("排名", "客户", "订单数", "消费金额"), rows);
    }

    /** format=xlsx 需要专业版（Excel 导出）；CSV 仍是标准版功能。 */
    private boolean xlsx(String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            planGuard.require(CurrentUser.requireTenantId(), com.smallbiz.billing.Feature.EXCEL_EXPORT);
            return true;
        }
        return false;
    }
}
