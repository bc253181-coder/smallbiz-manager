package com.smallbiz.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class PurchaseApiTest extends IntegrationTest {

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    TenantMemberRepository members;

    @Autowired
    TransactionTemplate tx;

    private Long product(Session s, String name, String cost, int stock) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"%s","unit":"个","costPrice":"%s","retailPrice":"50","lowStockThreshold":5,"initialStock":%d}
                """.formatted(name, cost, stock));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Long supplier(Session s, String name) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/suppliers"), s, "{\"name\":\"%s\"}".formatted(name));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private static String order(Long supplierId, boolean submit, String items) {
        return """
                {"supplierId":%d,"expectedDate":"2026-12-01","notes":"尽快发货","submit":%s,"items":[%s]}
                """.formatted(supplierId, submit, items);
    }

    private static String item(Long productId, int qty, String cost) {
        return "{\"productId\":%d,\"quantity\":%d,\"unitCost\":\"%s\"}".formatted(productId, qty, cost);
    }

    private MockHttpServletResponse create(Session s, String body) throws Exception {
        return send(post("/api/v1/purchase-orders"), s, body);
    }

    private Long createOk(Session s, String body) throws Exception {
        MockHttpServletResponse res = create(s, body);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private int stock(Session s, Long productId) throws Exception {
        return read(send(get("/api/v1/products/" + productId), s, null), "$.stockQty");
    }

    // ---------------- 新增 ----------------

    @Test
    void createDraftComputesTotalsAndNumbers() throws Exception {
        Session s = registerWithTenant("po-new", "采购店");
        Long sup = supplier(s, "华强配件厂");
        Long a = product(s, "数据线", "5", 0);
        Long b = product(s, "充电头", "12.5", 0);

        MockHttpServletResponse res = create(s, order(sup, false, item(a, 100, "4.80") + "," + item(b, 3, "12.35")));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        assertThat((String) read(res, "$.orderNo")).matches("PO-\\d{8}-0001");
        assertThat((String) read(res, "$.status")).isEqualTo("DRAFT");
        assertThat((String) read(res, "$.supplierName")).isEqualTo("华强配件厂");
        assertThat((String) read(res, "$.totalAmount")).isEqualTo("517.05"); // 480.00 + 37.05
        assertThat((Integer) read(res, "$.totalQuantity")).isEqualTo(103);
        assertThat((String) read(res, "$.items[1].lineTotal")).isEqualTo("37.05");
        assertThat((String) read(res, "$.items[0].productName")).isEqualTo("数据线");

        MockHttpServletResponse second = create(s, order(sup, true, item(a, 1, "5")));
        assertThat((String) read(second, "$.orderNo")).endsWith("-0002");
        assertThat((String) read(second, "$.status")).isEqualTo("ORDERED");

        // 草稿不影响库存
        assertThat(stock(s, a)).isZero();
    }

    @Test
    void createValidatesItems() throws Exception {
        Session s = registerWithTenant("po-val", "校验店");
        Long sup = supplier(s, "供应商");
        Long a = product(s, "商品", "1", 0);

        assertThat(create(s, order(sup, false, "")).getStatus()).isEqualTo(400);
        MockHttpServletResponse bad = create(s, order(sup, false, item(a, 0, "-1")));
        assertThat(bad.getStatus()).isEqualTo(400);
        MockHttpServletResponse dup = create(s, order(sup, false, item(a, 1, "1") + "," + item(a, 2, "1")));
        assertThat((String) read(dup, "$.code")).isEqualTo("DUPLICATE_PRODUCT");
        MockHttpServletResponse noProduct = create(s, order(sup, false, item(999999L, 1, "1")));
        assertThat(noProduct.getStatus()).isEqualTo(404);
        MockHttpServletResponse noSupplier = create(s, order(999999L, false, item(a, 1, "1")));
        assertThat((String) read(noSupplier, "$.code")).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    // ---------------- 状态流转 ----------------

    @Test
    void fullLifecycle_receiveAddsStockAndUpdatesCost() throws Exception {
        Session s = registerWithTenant("po-flow", "流程店");
        Long sup = supplier(s, "供应商");
        Long a = product(s, "耳机", "10.00", 10);
        Long id = createOk(s, order(sup, false, item(a, 30, "14.00")));

        // 草稿不能收货
        MockHttpServletResponse early = send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null);
        assertThat(early.getStatus()).isEqualTo(409);

        assertThat((String) read(send(post("/api/v1/purchase-orders/" + id + "/submit"), s, null), "$.status"))
                .isEqualTo("ORDERED");
        assertThat(send(post("/api/v1/purchase-orders/" + id + "/submit"), s, null).getStatus()).isEqualTo(409);

        MockHttpServletResponse received = send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null);
        assertThat(received.getStatus()).as(received.getContentAsString()).isEqualTo(200);
        assertThat((String) read(received, "$.status")).isEqualTo("RECEIVED");
        assertThat((Integer) read(received, "$.items[0].receivedQty")).isEqualTo(30);
        assertThat((String) read(received, "$.receivedByName")).isNotBlank();

        MockHttpServletResponse p = send(get("/api/v1/products/" + a), s, null);
        assertThat((Integer) read(p, "$.stockQty")).isEqualTo(40);
        assertThat((String) read(p, "$.costPrice")).isEqualTo("13.00"); // (10×10 + 30×14) / 40

        MockHttpServletResponse mv = send(get("/api/v1/inventory/movements?type=PURCHASE_RECEIVE"), s, null);
        assertThat((Integer) read(mv, "$.totalItems")).isEqualTo(1);
        assertThat((String) read(mv, "$.items[0].refType")).isEqualTo("PURCHASE_ORDER");
        assertThat(((Number) read(mv, "$.items[0].refId")).longValue()).isEqualTo(id);

        // 已收货：不能再收、不能改、不能取消、不能删
        assertThat(send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null).getStatus()).isEqualTo(409);
        assertThat(send(put("/api/v1/purchase-orders/" + id), s,
                order(sup, false, item(a, 1, "1")).replace("}\n", ",\"version\":0}")).getStatus()).isEqualTo(409);
        assertThat(send(post("/api/v1/purchase-orders/" + id + "/cancel"), s, "{}").getStatus()).isEqualTo(409);
        assertThat(send(delete("/api/v1/purchase-orders/" + id), s, null).getStatus()).isEqualTo(409);
        assertThat(stock(s, a)).isEqualTo(40);
        assertConsistent(s, a);
    }

    @Test
    void updateReplacesItems_cancelAndDeleteRules() throws Exception {
        Session s = registerWithTenant("po-upd", "修改店");
        Long sup = supplier(s, "供应商");
        Long a = product(s, "A", "1", 0);
        Long b = product(s, "B", "1", 0);
        Long id = createOk(s, order(sup, false, item(a, 5, "2")));

        String body = """
                {"supplierId":%d,"notes":"","submit":true,"version":%d,"items":[%s]}
                """;
        MockHttpServletResponse updated = send(put("/api/v1/purchase-orders/" + id), s,
                body.formatted(sup, 0, item(b, 7, "3") + "," + item(a, 1, "2")));
        assertThat(updated.getStatus()).as(updated.getContentAsString()).isEqualTo(200);
        assertThat((Integer) read(updated, "$.itemCount")).isEqualTo(2);
        assertThat((String) read(updated, "$.items[0].productName")).isEqualTo("B");
        assertThat((String) read(updated, "$.totalAmount")).isEqualTo("23.00");
        assertThat((String) read(updated, "$.status")).isEqualTo("ORDERED");
        assertThat((Object) read(updated, "$.notes")).isNull();

        MockHttpServletResponse stale = send(put("/api/v1/purchase-orders/" + id), s,
                body.formatted(sup, 0, item(a, 1, "1")));
        assertThat((String) read(stale, "$.code")).isEqualTo("CONCURRENT_MODIFICATION");

        // 已下单不能删除，只能取消
        assertThat(send(delete("/api/v1/purchase-orders/" + id), s, null).getStatus()).isEqualTo(409);
        MockHttpServletResponse cancelled = send(post("/api/v1/purchase-orders/" + id + "/cancel"), s,
                "{\"reason\":\"供应商缺货\"}");
        assertThat((String) read(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat((String) read(cancelled, "$.cancelReason")).isEqualTo("供应商缺货");
        assertThat(send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null).getStatus()).isEqualTo(409);
        assertThat(stock(s, a)).isZero();

        Long draft = createOk(s, order(sup, false, item(a, 1, "1")));
        assertThat(send(delete("/api/v1/purchase-orders/" + draft), s, null).getStatus()).isEqualTo(204);
        assertThat(send(get("/api/v1/purchase-orders/" + draft), s, null).getStatus()).isEqualTo(404);
    }

    @Test
    void receivingDeletedProductFailsAtomically() throws Exception {
        Session s = registerWithTenant("po-del", "删除商品店");
        Long sup = supplier(s, "供应商");
        Long a = product(s, "保留", "1", 0);
        Long b = product(s, "将被删除", "1", 0);
        Long id = createOk(s, order(sup, true, item(a, 5, "1") + "," + item(b, 5, "1")));
        send(delete("/api/v1/products/" + b), s, null);

        MockHttpServletResponse res = send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null);
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat((String) read(res, "$.code")).isEqualTo("PRODUCT_DELETED");
        // 整单回滚：没有删除的商品也没有入库
        assertThat(stock(s, a)).isZero();
        assertThat((String) read(send(get("/api/v1/purchase-orders/" + id), s, null), "$.status")).isEqualTo("ORDERED");
    }

    @Test
    void concurrentReceiveOnlyAddsStockOnce() throws Exception {
        Session s = registerWithTenant("po-conc", "并发收货店");
        Long sup = supplier(s, "供应商");
        Long a = product(s, "商品", "1", 0);
        Long id = createOk(s, order(sup, true, item(a, 10, "1")));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return send(post("/api/v1/purchase-orders/" + id + "/receive"), s, null).getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Integer> f : results) {
            if (f.get(30, TimeUnit.SECONDS) == 200) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(stock(s, a)).isEqualTo(10);
        assertConsistent(s, a);
    }

    // ---------------- 查询 ----------------

    @Test
    void listFiltersAndSupplierProducts() throws Exception {
        Session s = registerWithTenant("po-list", "列表店");
        Long s1 = supplier(s, "华强配件厂");
        Long s2 = supplier(s, "义乌批发");
        Long a = product(s, "数据线", "1", 0);
        Long b = product(s, "手机壳", "1", 0);
        Long r1 = createOk(s, order(s1, true, item(a, 10, "1") + "," + item(b, 4, "2")));
        Long r2 = createOk(s, order(s1, true, item(a, 5, "1.2")));
        createOk(s, order(s2, false, item(b, 1, "1")));
        send(post("/api/v1/purchase-orders/" + r1 + "/receive"), s, null);
        send(post("/api/v1/purchase-orders/" + r2 + "/receive"), s, null);

        assertThat((Integer) read(send(get("/api/v1/purchase-orders"), s, null), "$.totalItems")).isEqualTo(3);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders?status=RECEIVED"), s, null), "$.totalItems"))
                .isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders?supplierId=" + s2), s, null), "$.totalItems"))
                .isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders?q=华强"), s, null), "$.totalItems")).isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders?q=PO-"), s, null), "$.totalItems")).isEqualTo(3);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders?from=2099-01-01"), s, null), "$.totalItems"))
                .isZero();

        MockHttpServletResponse supplied = send(get("/api/v1/suppliers/" + s1 + "/products"), s, null);
        assertThat((List<?>) read(supplied, "$")).hasSize(2);
        MockHttpServletResponse cable = send(get("/api/v1/suppliers/" + s1 + "/products"), s, null);
        List<Integer> totals = read(cable, "$[?(@.productName == '数据线')].totalQuantity");
        assertThat(totals).containsExactly(15);
        assertThat((List<?>) read(send(get("/api/v1/suppliers/" + s2 + "/products"), s, null), "$")).isEmpty();
    }

    // ---------------- 权限与隔离 ----------------

    @Test
    void staffCannotAccessPurchasing() throws Exception {
        Session owner = registerWithTenant("po-own", "采购权限店");
        Long tenantId = currentTenantId(owner, mvc);
        Long sup = supplier(owner, "供应商");
        Long a = product(owner, "商品", "1", 0);
        Long id = createOk(owner, order(sup, true, item(a, 1, "1")));
        Session staff = register("po-staff");
        allowStaff(tenantId);
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session st = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        assertThat(send(get("/api/v1/purchase-orders"), st, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/purchase-orders/" + id), st, null).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/v1/purchase-orders/" + id + "/receive"), st, null).getStatus()).isEqualTo(403);
        assertThat(stock(owner, a)).isZero();
    }

    @Test
    void purchasesAreIsolatedBetweenTenants() throws Exception {
        Session a = registerWithTenant("po-iso-a", "采购A");
        Session b = registerWithTenant("po-iso-b", "采购B");
        Long supA = supplier(a, "A 的供应商");
        Long prodA = product(a, "A 的商品", "1", 0);
        Long poA = createOk(a, order(supA, true, item(prodA, 50, "1")));
        Long supB = supplier(b, "B 的供应商");
        Long prodB = product(b, "B 的商品", "1", 0);

        // B 不能看到、修改、收货、取消、删除 A 的采购单
        assertThat(send(get("/api/v1/purchase-orders/" + poA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/purchase-orders/" + poA + "/receive"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/purchase-orders/" + poA + "/cancel"), b, "{}").getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/purchase-orders/" + poA + "/submit"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(delete("/api/v1/purchase-orders/" + poA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/purchase-orders/" + poA), b, """
                {"supplierId":%d,"submit":false,"version":0,"items":[%s]}
                """.formatted(supB, item(prodB, 1, "1"))).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/purchase-orders"), b, null), "$.totalItems")).isZero();
        assertThat(send(get("/api/v1/suppliers/" + supA + "/products"), b, null).getStatus()).isEqualTo(404);

        // B 不能用 A 的供应商或 A 的商品下单
        assertThat(create(b, order(supA, false, item(prodB, 1, "1"))).getStatus()).isEqualTo(404);
        assertThat(create(b, order(supB, false, item(prodA, 1, "1"))).getStatus()).isEqualTo(404);

        // A 的数据完好
        assertThat((String) read(send(get("/api/v1/purchase-orders/" + poA), a, null), "$.status")).isEqualTo("ORDERED");
        assertThat(stock(a, prodA)).isZero();
    }

    private void assertConsistent(Session s, Long productId) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> {
            Product p = products.findById(productId).orElseThrow();
            assertThat((long) p.getStockQty()).isEqualTo(movements.sumChangeByProductId(productId));
        }));
    }
}
