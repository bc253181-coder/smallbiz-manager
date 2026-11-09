package com.smallbiz.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.inventory.StockMovementRepository;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantContext;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

class SalesApiTest extends IntegrationTest {

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    TenantMemberRepository members;

    @Autowired
    TransactionTemplate tx;

    /** 零售 15.00、批发 8.00（可为空）、成本 4.50。 */
    private Long product(Session s, String name, int stock, String wholesale) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"%s","unit":"条","costPrice":"4.50","retailPrice":"15.00","wholesalePrice":%s,
                 "lowStockThreshold":2,"initialStock":%d}
                """.formatted(name, wholesale == null ? "null" : "\"" + wholesale + "\"", stock));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Long customer(Session s, String name, String tier) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/customers"), s,
                "{\"name\":\"%s\",\"customerType\":\"BUSINESS\",\"priceTier\":\"%s\"}".formatted(name, tier));
        return ((Number) read(res, "$.id")).longValue();
    }

    private static String sale(Long customerId, String tier, String discount, String items) {
        return """
                {"customerId":%s,"priceTier":"%s","discountAmount":%s,"notes":"测试","items":[%s]}
                """.formatted(customerId, tier, discount == null ? "null" : "\"" + discount + "\"", items);
    }

    private static String item(Long productId, int qty, String price) {
        return "{\"productId\":%d,\"quantity\":%d,\"unitPrice\":%s}".formatted(productId, qty,
                price == null ? "null" : "\"" + price + "\"");
    }

    private MockHttpServletResponse sell(Session s, String body) throws Exception {
        return send(post("/api/v1/sales-orders"), s, body);
    }

    private Long sellOk(Session s, String body) throws Exception {
        MockHttpServletResponse res = sell(s, body);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private int stock(Session s, Long id) throws Exception {
        return read(send(get("/api/v1/products/" + id), s, null), "$.stockQty");
    }

    // ---------------- 开单 ----------------

    @Test
    void retailWalkInSale_deductsStockAndRecordsCostSnapshot() throws Exception {
        Session s = registerWithTenant("so-retail", "零售店");
        Long cable = product(s, "数据线", 20, "8.00");
        Long charger = product(s, "充电头", 5, null);

        MockHttpServletResponse res = sell(s, sale(null, "RETAIL", "2.00",
                item(cable, 3, null) + "," + item(charger, 1, "13.80")));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        assertThat((String) read(res, "$.orderNo")).matches("SO-\\d{8}-0001");
        assertThat((String) read(res, "$.status")).isEqualTo("COMPLETED");
        assertThat((Object) read(res, "$.customerName")).isNull();
        assertThat((String) read(res, "$.items[0].unitPrice")).isEqualTo("15.00"); // 默认零售价
        assertThat((String) read(res, "$.items[1].unitPrice")).isEqualTo("13.80"); // 手动改价
        assertThat((String) read(res, "$.subtotal")).isEqualTo("58.80");          // 45.00 + 13.80
        assertThat((String) read(res, "$.totalAmount")).isEqualTo("56.80");       // −2.00 优惠
        assertThat((String) read(res, "$.paidAmount")).isEqualTo("56.80");
        assertThat((String) read(res, "$.totalCost")).isEqualTo("18.00");         // 4 × 4.50
        assertThat((String) read(res, "$.grossProfit")).isEqualTo("38.80");
        assertThat((String) read(res, "$.items[0].unitCost")).isEqualTo("4.50");

        assertThat(stock(s, cable)).isEqualTo(17);
        assertThat(stock(s, charger)).isEqualTo(4);
        MockHttpServletResponse mv = send(get("/api/v1/inventory/movements?type=SALE"), s, null);
        assertThat((Integer) read(mv, "$.totalItems")).isEqualTo(2);
        assertThat((String) read(mv, "$.items[0].refType")).isEqualTo("SALES_ORDER");
        assertConsistent(s, cable);
    }

    @Test
    void wholesaleCustomerGetsWholesalePrice_fallingBackToRetail() throws Exception {
        Session s = registerWithTenant("so-whole", "批发店");
        Long cable = product(s, "数据线", 100, "8.00");
        Long noWholesale = product(s, "无批发价", 100, null);
        Long cust = customer(s, "老王维修店", "WHOLESALE");

        MockHttpServletResponse res = sell(s, sale(cust, "WHOLESALE", null,
                item(cable, 50, null) + "," + item(noWholesale, 2, null)));
        assertThat((String) read(res, "$.customerName")).isEqualTo("老王维修店");
        assertThat((String) read(res, "$.items[0].unitPrice")).isEqualTo("8.00");
        assertThat((String) read(res, "$.items[1].unitPrice")).isEqualTo("15.00");
        assertThat((String) read(res, "$.totalAmount")).isEqualTo("430.00");
        assertThat((String) read(res, "$.discountAmount")).isEqualTo("0.00");
    }

    @Test
    void insufficientStockRejectsWholeOrder() throws Exception {
        Session s = registerWithTenant("so-short", "缺货店");
        Long plenty = product(s, "充足", 100, null);
        Long scarce = product(s, "紧缺", 2, null);

        MockHttpServletResponse res = sell(s, sale(null, "RETAIL", null, item(plenty, 10, null) + "," + item(scarce, 3, null)));
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat((String) read(res, "$.code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(((Number) read(res, "$.details.productId")).longValue()).isEqualTo(scarce);

        // 整单回滚：充足的商品也没有被扣，订单和单号都没有产生
        assertThat(stock(s, plenty)).isEqualTo(100);
        assertThat((Integer) read(send(get("/api/v1/sales-orders"), s, null), "$.totalItems")).isZero();
        MockHttpServletResponse next = sell(s, sale(null, "RETAIL", null, item(plenty, 1, null)));
        assertThat((String) read(next, "$.orderNo")).endsWith("-0001");
    }

    @Test
    void validationRules() throws Exception {
        Session s = registerWithTenant("so-val", "校验店");
        Long p = product(s, "商品", 10, null);

        assertThat(sell(s, sale(null, "RETAIL", null, "")).getStatus()).isEqualTo(400);
        assertThat(sell(s, sale(null, "RETAIL", null, item(p, 0, "-1"))).getStatus()).isEqualTo(400);
        assertThat((String) read(sell(s, sale(null, "RETAIL", null, item(p, 1, null) + "," + item(p, 1, null))), "$.code"))
                .isEqualTo("DUPLICATE_PRODUCT");
        assertThat((String) read(sell(s, sale(null, "RETAIL", "15.01", item(p, 1, null))), "$.code"))
                .isEqualTo("DISCOUNT_TOO_LARGE");
        assertThat(sell(s, sale(999999L, "RETAIL", null, item(p, 1, null))).getStatus()).isEqualTo(404);

        // 下架商品不能销售
        send(put("/api/v1/products/" + p), s, """
                {"name":"商品","sku":"SKU000001","unit":"条","costPrice":"4.5","retailPrice":"15","lowStockThreshold":2,
                 "status":"INACTIVE","version":0}
                """);
        assertThat((String) read(sell(s, sale(null, "RETAIL", null, item(p, 1, null))), "$.code")).isEqualTo("PRODUCT_INACTIVE");
        assertThat(stock(s, p)).isEqualTo(10);
    }

    // ---------------- 取消 ----------------

    @Test
    void cancelRestoresStockOnce() throws Exception {
        Session s = registerWithTenant("so-cancel", "取消店");
        Long p = product(s, "商品", 10, null);
        Long id = sellOk(s, sale(null, "RETAIL", null, item(p, 4, null)));
        assertThat(stock(s, p)).isEqualTo(6);

        MockHttpServletResponse c = send(post("/api/v1/sales-orders/" + id + "/cancel"), s, "{\"reason\":\"客户退货\"}");
        assertThat(c.getStatus()).as(c.getContentAsString()).isEqualTo(200);
        assertThat((String) read(c, "$.status")).isEqualTo("CANCELLED");
        assertThat((String) read(c, "$.cancelReason")).isEqualTo("客户退货");
        assertThat(stock(s, p)).isEqualTo(10);

        assertThat(send(post("/api/v1/sales-orders/" + id + "/cancel"), s, "{}").getStatus()).isEqualTo(409);
        assertThat(stock(s, p)).isEqualTo(10);
        assertThat((Integer) read(send(get("/api/v1/inventory/movements?type=SALE_CANCEL"), s, null), "$.totalItems"))
                .isEqualTo(1);
        assertConsistent(s, p);
    }

    // ---------------- 查询与客户统计 ----------------

    @Test
    void listFiltersAndCustomerStatsExcludeCancelled() throws Exception {
        Session s = registerWithTenant("so-list", "列表店");
        Long p = product(s, "商品", 100, null);
        Long wang = customer(s, "王老板", "RETAIL");
        Long li = customer(s, "李小姐", "RETAIL");
        sellOk(s, sale(wang, "RETAIL", null, item(p, 2, null)));                 // 30.00
        Long cancelled = sellOk(s, sale(wang, "RETAIL", null, item(p, 10, null)));
        sellOk(s, sale(li, "RETAIL", null, item(p, 1, null)));
        sellOk(s, sale(null, "RETAIL", null, item(p, 1, null)));
        send(post("/api/v1/sales-orders/" + cancelled + "/cancel"), s, "{}");

        assertThat((Integer) read(send(get("/api/v1/sales-orders"), s, null), "$.totalItems")).isEqualTo(4);
        assertThat((Integer) read(send(get("/api/v1/sales-orders?status=CANCELLED"), s, null), "$.totalItems")).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/sales-orders?customerId=" + wang), s, null), "$.totalItems")).isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/sales-orders?q=王"), s, null), "$.totalItems")).isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/sales-orders?from=2099-01-01"), s, null), "$.totalItems")).isZero();

        MockHttpServletResponse stats = send(get("/api/v1/customers/" + wang + "/stats"), s, null);
        assertThat((Integer) read(stats, "$.orderCount")).isEqualTo(1);
        assertThat((String) read(stats, "$.totalSpent")).isEqualTo("30.00");
        assertThat((Object) read(stats, "$.lastOrderAt")).isNotNull();
    }

    // ---------------- 并发 ----------------

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Session s = registerWithTenant("so-conc", "并发店");
        Long p = product(s, "爆款", 10, null);
        int threads = 8; // 每单买 3 件，库存只够 3 单
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return sell(s, sale(null, "RETAIL", null, item(p, 3, null))).getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Integer> f : results) {
            if (f.get(30, TimeUnit.SECONDS) == 201) {
                ok++;
            }
        }
        pool.shutdown();
        // 回归测试：开单时先读过商品（取价格），加锁后仍必须读到最新库存，否则会按旧库存计算而超卖
        List<Integer> after = read(send(get("/api/v1/inventory/movements?productId=" + p), s, null), "$.items[*].qtyAfter");
        assertThat(after).containsExactly(1, 4, 7, 10);
        assertThat(ok).isEqualTo(3);
        assertThat(stock(s, p)).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/sales-orders"), s, null), "$.totalItems")).isEqualTo(3);
        assertConsistent(s, p);
    }

    // ---------------- 权限与隔离 ----------------

    @Test
    void staffCanSellButNotCancel_andSeesNoCostOrProfit() throws Exception {
        Session owner = registerWithTenant("so-own", "权限店");
        Long tenantId = currentTenantId(owner, mvc);
        Long p = product(owner, "商品", 10, null);
        Session staff = register("so-staff");
        allowStaff(tenantId);
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session st = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        MockHttpServletResponse res = sell(st, sale(null, "RETAIL", null, item(p, 1, null)));
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat((Object) read(res, "$.totalCost")).isNull();
        assertThat((Object) read(res, "$.grossProfit")).isNull();
        assertThat((Object) read(res, "$.items[0].unitCost")).isNull();
        Long id = ((Number) read(res, "$.id")).longValue();
        assertThat((Object) read(send(get("/api/v1/sales-orders/" + id), st, null), "$.grossProfit")).isNull();

        assertThat(send(post("/api/v1/sales-orders/" + id + "/cancel"), st, "{}").getStatus()).isEqualTo(403);
        assertThat(stock(owner, p)).isEqualTo(9);
        // 店主可以看到毛利
        assertThat((String) read(send(get("/api/v1/sales-orders/" + id), owner, null), "$.grossProfit")).isEqualTo("10.50");
    }

    @Test
    void salesAreIsolatedBetweenTenants() throws Exception {
        Session a = registerWithTenant("so-iso-a", "销售A");
        Session b = registerWithTenant("so-iso-b", "销售B");
        Long prodA = product(a, "A 的商品", 10, null);
        Long custA = customer(a, "A 的客户", "RETAIL");
        Long saleA = sellOk(a, sale(custA, "RETAIL", null, item(prodA, 1, null)));
        Long prodB = product(b, "B 的商品", 10, null);

        assertThat(send(get("/api/v1/sales-orders/" + saleA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/sales-orders/" + saleA + "/cancel"), b, "{}").getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/sales-orders"), b, null), "$.totalItems")).isZero();
        assertThat(send(get("/api/v1/customers/" + custA + "/stats"), b, null).getStatus()).isEqualTo(404);
        // B 不能卖 A 的商品，也不能把单记到 A 的客户名下
        assertThat(sell(b, sale(null, "RETAIL", null, item(prodA, 1, null))).getStatus()).isEqualTo(404);
        assertThat(sell(b, sale(custA, "RETAIL", null, item(prodB, 1, null))).getStatus()).isEqualTo(404);

        assertThat(stock(a, prodA)).isEqualTo(9);
        assertThat((String) read(send(get("/api/v1/sales-orders/" + saleA), a, null), "$.status")).isEqualTo("COMPLETED");
    }

    private void assertConsistent(Session s, Long productId) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> {
            Product p = products.findById(productId).orElseThrow();
            assertThat((long) p.getStockQty()).isEqualTo(movements.sumChangeByProductId(productId));
        }));
    }
}
