package com.smallbiz.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.catalog.Category;
import com.smallbiz.catalog.CategoryRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.report.ReportDtos.CategoryStock;
import com.smallbiz.report.ReportDtos.CustomerRank;
import com.smallbiz.report.ReportDtos.DashboardSummary;
import com.smallbiz.report.ReportDtos.ProductRank;
import com.smallbiz.report.ReportDtos.PurchasePoint;
import com.smallbiz.report.ReportDtos.PurchaseReport;
import com.smallbiz.report.ReportDtos.SalesPoint;
import com.smallbiz.report.ReportDtos.SalesReport;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

/**
 * 经营报表。日期都按店铺时区解释（"今天""本月"是店铺当地的今天和本月）。
 * 已取消的销售单不计入；采购按"收货时间"统计已收货的采购单。
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public enum GroupBy { DAY, MONTH }

    public static final int MAX_DAYS = 366;

    private final ReportRepository reports;
    private final TenantRepository tenants;
    private final CategoryRepository categories;

    public ReportService(ReportRepository reports, TenantRepository tenants, CategoryRepository categories) {
        this.reports = reports;
        this.tenants = tenants;
        this.categories = categories;
    }

    /** 已校验的日期区间（含首尾两天）。 */
    record Range(LocalDate from, LocalDate to, ZoneId zone) {
        Instant start() {
            return from.atStartOfDay(zone).toInstant();
        }

        Instant end() {
            return to.plusDays(1).atStartOfDay(zone).toInstant();
        }
    }

    Range range(Long tenantId, LocalDate from, LocalDate to) {
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(zone);
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today;
        if (f.isAfter(t)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LONG", "日期范围最长 366 天");
        }
        return new Range(f, t, zone);
    }

    // ---------------- 仪表盘 ----------------

    public DashboardSummary dashboard(Long tenantId, boolean hideCost) {
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(zone);
        Range month = new Range(today.withDayOfMonth(1), today, zone);
        Instant todayStart = today.atStartOfDay(zone).toInstant();

        BigDecimal todaySales = BigDecimal.ZERO;
        BigDecimal monthSales = BigDecimal.ZERO;
        BigDecimal monthCost = BigDecimal.ZERO;
        long todayOrders = 0;
        long monthOrders = 0;
        for (Object[] r : reports.salesRows(month.start(), month.end())) {
            Instant at = (Instant) r[0];
            BigDecimal amount = (BigDecimal) r[1];
            monthSales = monthSales.add(amount);
            monthCost = monthCost.add((BigDecimal) r[2]);
            monthOrders++;
            if (!at.isBefore(todayStart)) {
                todaySales = todaySales.add(amount);
                todayOrders++;
            }
        }
        BigDecimal purchases = reports.purchaseRows(month.start(), month.end()).stream()
                .map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardSummary(scale(todaySales), todayOrders, scale(monthSales), monthOrders,
                hideCost ? null : scale(monthSales.subtract(monthCost)), hideCost ? null : scale(purchases), today);
    }

    /** 最近 N 天每日销售（含今天），没有销售的日期补 0。 */
    public SalesReport salesTrend(Long tenantId, int days, boolean hideCost) {
        int n = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now(zone(tenantId));
        return sales(tenantId, today.minusDays(n - 1L), today, GroupBy.DAY, hideCost);
    }

    // ---------------- 报表 ----------------

    public SalesReport sales(Long tenantId, LocalDate from, LocalDate to, GroupBy groupBy, boolean hideCost) {
        Range r = range(tenantId, from, to);
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (String key : periods(r, groupBy)) {
            counts.put(key, new long[1]);
            sums.put(key, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Object[] row : reports.salesRows(r.start(), r.end())) {
            String key = periodOf((Instant) row[0], r.zone(), groupBy);
            counts.get(key)[0]++;
            BigDecimal[] s = sums.get(key);
            s[0] = s[0].add((BigDecimal) row[1]);
            s[1] = s[1].add((BigDecimal) row[2]);
        }
        List<SalesPoint> points = new ArrayList<>();
        long totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (String key : counts.keySet()) {
            BigDecimal[] s = sums.get(key);
            points.add(point(key, counts.get(key)[0], s[0], s[1], hideCost));
            totalCount += counts.get(key)[0];
            totalAmount = totalAmount.add(s[0]);
            totalCost = totalCost.add(s[1]);
        }
        return new SalesReport(r.from(), r.to(), groupBy.name(), points,
                point("合计", totalCount, totalAmount, totalCost, hideCost));
    }

    public PurchaseReport purchases(Long tenantId, LocalDate from, LocalDate to, GroupBy groupBy) {
        Range r = range(tenantId, from, to);
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (String key : periods(r, groupBy)) {
            counts.put(key, new long[1]);
            sums.put(key, BigDecimal.ZERO);
        }
        for (Object[] row : reports.purchaseRows(r.start(), r.end())) {
            String key = periodOf((Instant) row[0], r.zone(), groupBy);
            counts.get(key)[0]++;
            sums.merge(key, (BigDecimal) row[1], BigDecimal::add);
        }
        List<PurchasePoint> points = counts.keySet().stream()
                .map(k -> new PurchasePoint(k, counts.get(k)[0], scale(sums.get(k)))).toList();
        long count = points.stream().mapToLong(PurchasePoint::orderCount).sum();
        BigDecimal total = sums.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseReport(r.from(), r.to(), groupBy.name(), points, new PurchasePoint("合计", count, scale(total)));
    }

    /** 商品销售排行；by = amount（销售额）或 qty（数量）。 */
    public List<ProductRank> topProducts(Long tenantId, LocalDate from, LocalDate to, String by, int limit) {
        Range r = range(tenantId, from, to);
        Comparator<ProductRank> order = "qty".equals(by)
                ? Comparator.comparingLong(ProductRank::quantity).reversed()
                : Comparator.comparing(ProductRank::amount).reversed();
        return reports.productRows(r.start(), r.end()).stream()
                .map(x -> new ProductRank((Long) x[0], (String) x[1], (String) x[2], (String) x[3],
                        ((Number) x[4]).longValue(), scale((BigDecimal) x[5]),
                        scale(((BigDecimal) x[5]).subtract((BigDecimal) x[6]))))
                .sorted(order.thenComparing(ProductRank::productId))
                .limit(clampLimit(limit))
                .toList();
    }

    public List<CustomerRank> topCustomers(Long tenantId, LocalDate from, LocalDate to, int limit) {
        Range r = range(tenantId, from, to);
        return reports.customerRows(r.start(), r.end()).stream()
                .limit(clampLimit(limit))
                .map(x -> new CustomerRank((Long) x[0], (String) x[1], ((Number) x[2]).longValue(),
                        scale((BigDecimal) x[3])))
                .toList();
    }

    public List<CategoryStock> inventoryByCategory() {
        Map<Long, Category> byId = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        return reports.inventoryByCategory().stream()
                .map(x -> {
                    Long cid = (Long) x[0];
                    return new CategoryStock(cid, categoryName(cid, byId), ((Number) x[1]).longValue(),
                            x[2] == null ? 0 : ((Number) x[2]).longValue(), scale(toDecimal(x[3])));
                })
                .sorted(Comparator.comparing(CategoryStock::value).reversed())
                .toList();
    }

    // ---------------- 内部 ----------------

    private static String categoryName(Long id, Map<Long, Category> byId) {
        if (id == null || !byId.containsKey(id)) {
            return "未分类";
        }
        Category c = byId.get(id);
        Category parent = c.getParentId() == null ? null : byId.get(c.getParentId());
        return parent == null ? c.getName() : parent.getName() + " / " + c.getName();
    }

    private static List<String> periods(Range r, GroupBy g) {
        List<String> keys = new ArrayList<>();
        if (g == GroupBy.MONTH) {
            for (YearMonth m = YearMonth.from(r.from()); !m.isAfter(YearMonth.from(r.to())); m = m.plusMonths(1)) {
                keys.add(m.toString());
            }
        } else {
            for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
                keys.add(d.toString());
            }
        }
        return keys;
    }

    private static String periodOf(Instant at, ZoneId zone, GroupBy g) {
        LocalDate d = at.atZone(zone).toLocalDate();
        return g == GroupBy.MONTH ? YearMonth.from(d).toString() : d.toString();
    }

    private static SalesPoint point(String key, long count, BigDecimal amount, BigDecimal cost, boolean hideCost) {
        return new SalesPoint(key, count, scale(amount), hideCost ? null : scale(cost),
                hideCost ? null : scale(amount.subtract(cost)));
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal toDecimal(Object o) {
        return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getTimezone).map(ZoneId::of).orElse(ZoneId.of("Asia/Shanghai"));
    }
}
