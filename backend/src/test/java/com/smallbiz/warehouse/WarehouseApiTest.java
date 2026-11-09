package com.smallbiz.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/**
 * 多仓库：默认仓库、按仓库开单 / 收货 / 入库 / 盘点、调拨、停用、套餐上限与到期、并发、店员、店铺隔离。
 * 每个测试结束都核对两个不变量：商品总库存 = 各仓库之和；仓库库存 = 该仓库流水之和。
 */
class WarehouseApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    // ---------------- 辅助 ----------------

    private long product(Session s, String sku, int stock) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"仓库商品 %s","sku":"%s","unit":"个","costPrice":"5","retailPrice":"10","lowStockThreshold":0,"initialStock":%d}
                """.formatted(sku, sku, stock));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private MockHttpServletResponse createWarehouse(Session s, String name) throws Exception {
        return send(post("/api/v1/warehouses"), s, "{\"name\":\"%s\",\"address\":\"\"}".formatted(name));
    }

    private long warehouse(Session s, String name) throws Exception {
        MockHttpServletResponse res = createWarehouse(s, name);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private long defaultWarehouse(Session s) throws Exception {
        List<Number> ids = read(send(get("/api/v1/warehouses"), s, null), "$.warehouses[?(@.isDefault == true)].id");
        return ids.get(0).longValue();
    }

    private MockHttpServletResponse transfer(Session s, long from, long to, String items) throws Exception {
        return send(post("/api/v1/stock-transfers"), s,
                "{\"fromWarehouseId\":%d,\"toWarehouseId\":%d,\"notes\":\"补货\",\"items\":[%s]}".formatted(from, to, items));
    }

    private static String item(long productId, int qty) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, qty);
    }

    private MockHttpServletResponse sale(Session s, Long warehouseId, long productId, int qty) throws Exception {
        return send(post("/api/v1/sales-orders"), s, """
                {"priceTier":"RETAIL","warehouseId":%s,"items":[{"productId":%d,"quantity":%d}]}
                """.formatted(warehouseId == null ? "null" : warehouseId.toString(), productId, qty));
    }

    private int qty(long productId, long warehouseId) {
        List<Integer> r = jdbcTemplate.queryForList("select qty from product_stocks where product_id = ? and warehouse_id = ?",
                Integer.class, productId, warehouseId);
        return r.isEmpty() ? 0 : r.get(0);
    }

    private int total(long productId) {
        return jdbcTemplate.queryForObject("select stock_qty from products where id = ?", Integer.class, productId);
    }

    private void assertInvariants(Session s) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from products p where p.tenant_id = ? and p.stock_qty <>
                  (select coalesce(sum(ps.qty), 0) from product_stocks ps where ps.product_id = p.id)
                """, Long.class, tenantId)).as("总库存 = 各仓库之和").isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from product_stocks ps where ps.tenant_id = ? and ps.qty <>
                  (select coalesce(sum(m.qty_change), 0) from stock_movements m
                   where m.product_id = ps.product_id and m.warehouse_id = ps.warehouse_id)
                """, Long.class, tenantId)).as("仓库库存 = 该仓库流水之和").isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from stock_movements m where m.tenant_id = ? and not exists
                  (select 1 from product_stocks ps where ps.product_id = m.product_id and ps.warehouse_id = m.warehouse_id)
                """, Long.class, tenantId)).as("每条流水都有对应的仓库库存").isZero();
    }

    private Session staffOf(Session owner) throws Exception {
        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("wh-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        return new Session(staff.email(), staff.password(),
                read(send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null), "$.accessToken"), null, staff.userId());
    }

    // ---------------- 测试 ----------------

    @Test
    void everyStoreHasOneDefaultWarehouseAndSingleWarehouseWorksAsBefore() throws Exception {
        Session s = registerWithFreeTenant("wh-free", "单仓店");
        MockHttpServletResponse list = send(get("/api/v1/warehouses"), s, null);
        assertThat((List<?>) read(list, "$.warehouses")).hasSize(1);
        assertThat((String) read(list, "$.warehouses[0].name")).isEqualTo("主仓库");
        assertThat((Boolean) read(list, "$.warehouses[0].isDefault")).isTrue();
        assertThat((Integer) read(list, "$.max")).isEqualTo(1);
        assertThat((Boolean) read(list, "$.allowed")).isFalse();

        long main = defaultWarehouse(s);
        long p = product(s, "WH-F1", 10);
        assertThat(qty(p, main)).isEqualTo(10);
        // 不传仓库 = 默认仓库
        assertThat(send(post("/api/v1/inventory/stock-in"), s, "{\"productId\":%d,\"quantity\":5,\"unitCost\":\"5\"}".formatted(p)).getStatus()).isEqualTo(201);
        assertThat(sale(s, null, p, 4).getStatus()).isEqualTo(201);
        assertThat(qty(p, main)).isEqualTo(11);
        assertThat(total(p)).isEqualTo(11);
        assertThat((Integer) read(send(get("/api/v1/warehouses"), s, null), "$.warehouses[0].totalQty")).isEqualTo(11);

        MockHttpServletResponse denied = createWarehouse(s, "门店");
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat((String) read(denied, "$.details.requiredPlan")).isEqualTo("LIFETIME");
        assertInvariants(s);
    }

    @Test
    void stockMovesPerWarehouseAcrossSalesTransfersAndPurchases() throws Exception {
        Session s = registerWithProTenant("wh-flow", "多仓店");
        long main = defaultWarehouse(s);
        long store = warehouse(s, "门店");
        long p = product(s, "WH-A", 50);

        // 主仓库 → 门店 20
        MockHttpServletResponse tf = transfer(s, main, store, item(p, 20));
        assertThat(tf.getStatus()).as(tf.getContentAsString()).isEqualTo(201);
        assertThat((String) read(tf, "$.transferNo")).matches("TF-\\d{8}-\\d{4}");
        assertThat((String) read(tf, "$.fromWarehouseName")).isEqualTo("主仓库");
        assertThat(qty(p, main)).isEqualTo(30);
        assertThat(qty(p, store)).isEqualTo(20);
        assertThat(total(p)).isEqualTo(50);

        // 门店开单 3 → 门店 17；门店不够 18 被拒（虽然总库存够）
        MockHttpServletResponse sold = sale(s, store, p, 3);
        assertThat(sold.getStatus()).isEqualTo(201);
        assertThat((String) read(sold, "$.warehouseName")).isEqualTo("门店");
        assertThat(qty(p, store)).isEqualTo(17);
        MockHttpServletResponse tooMany = sale(s, store, p, 18);
        assertThat(tooMany.getStatus()).isEqualTo(409);
        assertThat((String) read(tooMany, "$.code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat((Integer) read(tooMany, "$.details.available")).isEqualTo(17);

        // 取消销售回到门店
        long saleId = ((Number) read(sold, "$.id")).longValue();
        assertThat(send(post("/api/v1/sales-orders/" + saleId + "/cancel"), s, "{}").getStatus()).isEqualTo(200);
        assertThat(qty(p, store)).isEqualTo(20);

        // 门店入库 5、盘点为 12
        MockHttpServletResponse in = send(post("/api/v1/inventory/stock-in"), s,
                "{\"productId\":%d,\"quantity\":5,\"unitCost\":\"5\",\"warehouseId\":%d}".formatted(p, store));
        assertThat((Integer) read(in, "$.warehouseQty")).isEqualTo(25);
        assertThat((Integer) read(in, "$.stockQty")).isEqualTo(55);
        MockHttpServletResponse adj = send(post("/api/v1/inventory/adjust"), s,
                "{\"productId\":%d,\"actualQty\":12,\"reason\":\"盘点\",\"warehouseId\":%d}".formatted(p, store));
        assertThat((Integer) read(adj, "$.qtyChange")).isEqualTo(-13);
        assertThat(qty(p, main)).isEqualTo(30);
        assertThat(total(p)).isEqualTo(42);

        // 采购收货到门店
        long sup = ((Number) read(send(post("/api/v1/suppliers"), s, "{\"name\":\"供货商\"}"), "$.id")).longValue();
        MockHttpServletResponse po = send(post("/api/v1/purchase-orders"), s, """
                {"supplierId":%d,"submit":true,"items":[{"productId":%d,"quantity":8,"unitCost":"5"}]}
                """.formatted(sup, p));
        long poId = ((Number) read(po, "$.id")).longValue();
        MockHttpServletResponse received = send(post("/api/v1/purchase-orders/" + poId + "/receive"), s,
                "{\"warehouseId\":%d}".formatted(store));
        assertThat(received.getStatus()).as(received.getContentAsString()).isEqualTo(200);
        assertThat((String) read(received, "$.warehouseName")).isEqualTo("门店");
        assertThat(qty(p, store)).isEqualTo(20);
        assertThat(total(p)).isEqualTo(50);

        // 各仓库库存、按仓库的流水
        List<Map<String, Object>> stocks = read(send(get("/api/v1/products/" + p + "/stocks"), s, null), "$");
        assertThat(stocks).extracting(m -> m.get("warehouseName") + "=" + m.get("qty")).containsExactly("主仓库=30", "门店=20");
        assertThat((Integer) read(send(get("/api/v1/inventory/movements").param("warehouseId", String.valueOf(store)), s, null),
                "$.totalItems")).isEqualTo(6); // 调入、销售、取消、入库、盘点、收货
        List<Map<String, Object>> q = read(send(get("/api/v1/warehouses/" + store + "/stocks").param("productIds", String.valueOf(p)), s, null), "$");
        assertThat(q.get(0)).containsEntry("qty", 20);
        assertThat((Integer) read(send(get("/api/v1/stock-transfers"), s, null), "$.totalItems")).isEqualTo(1);
        assertInvariants(s);
    }

    @Test
    void transferRules() throws Exception {
        Session s = registerWithProTenant("wh-rule", "调拨规则店");
        jdbcTemplate.update("update tenants set allow_negative_stock = true where id = ?", currentTenantId(s, mvc));
        long main = defaultWarehouse(s);
        long store = warehouse(s, "门店");
        long a = product(s, "WH-R1", 10);
        long b = product(s, "WH-R2", 2);

        assertThat((String) read(transfer(s, main, main, item(a, 1)), "$.code")).isEqualTo("SAME_WAREHOUSE");
        assertThat((String) read(transfer(s, main, store, item(a, 1) + "," + item(a, 2)), "$.code")).isEqualTo("DUPLICATE_PRODUCT");
        // 第二个商品不够：整单回滚（即使店铺允许负库存，调拨也不能调成负数）
        MockHttpServletResponse partial = transfer(s, main, store, item(a, 5) + "," + item(b, 3));
        assertThat(partial.getStatus()).isEqualTo(409);
        assertThat(qty(a, main)).isEqualTo(10);
        assertThat(qty(a, store)).isZero();
        assertThat(count("select count(*) from stock_transfers where tenant_id = ?", currentTenantId(s, mvc))).isZero();
        assertThat(transfer(s, main, 999_999_999L, item(a, 1)).getStatus()).isEqualTo(404);
        assertInvariants(s);
    }

    @Test
    void deactivateRequiresEmptyNonDefaultWarehouse() throws Exception {
        Session s = registerWithProTenant("wh-off", "停用店");
        long main = defaultWarehouse(s);
        long store = warehouse(s, "门店");
        long p = product(s, "WH-D1", 10);
        transfer(s, main, store, item(p, 4));

        assertThat((String) read(send(post("/api/v1/warehouses/" + main + "/deactivate"), s, null), "$.code")).isEqualTo("DEFAULT_WAREHOUSE");
        assertThat((String) read(send(post("/api/v1/warehouses/" + store + "/deactivate"), s, null), "$.code")).isEqualTo("WAREHOUSE_HAS_STOCK");
        transfer(s, store, main, item(p, 4));
        assertThat(send(post("/api/v1/warehouses/" + store + "/deactivate"), s, null).getStatus()).isEqualTo(204);
        assertThat((String) read(sale(s, store, p, 1), "$.code")).isEqualTo("WAREHOUSE_INACTIVE");
        // 改名、重名
        assertThat(send(put("/api/v1/warehouses/" + main), s, "{\"name\":\"总仓\"}").getStatus()).isEqualTo(200);
        assertThat((String) read(createWarehouse(s, "总仓"), "$.code")).isEqualTo("WAREHOUSE_NAME_EXISTS");
        assertInvariants(s);
    }

    @Test
    void proLimitAndDowngradeOnlyAllowsTransferOut() throws Exception {
        Session s = registerWithProTenant("wh-lim", "上限店");
        long main = defaultWarehouse(s);
        long store = warehouse(s, "门店");
        for (int i = 2; i <= 4; i++) {
            warehouse(s, "仓库" + i);
        }
        MockHttpServletResponse sixth = createWarehouse(s, "第六个");
        assertThat(sixth.getStatus()).isEqualTo(409);
        assertThat((String) read(sixth, "$.code")).isEqualTo("WAREHOUSE_LIMIT_REACHED");

        long p = product(s, "WH-L1", 10);
        transfer(s, main, store, item(p, 6));
        jdbcTemplate.update("update tenants set plan = 'STANDARD' where id = ?", currentTenantId(s, mvc));

        assertThat((String) read(sale(s, store, p, 1), "$.code")).isEqualTo("PLAN_FEATURE_REQUIRED");
        assertThat(send(post("/api/v1/inventory/stock-in"), s,
                "{\"productId\":%d,\"quantity\":1,\"warehouseId\":%d}".formatted(p, store)).getStatus()).isEqualTo(403);
        assertThat(transfer(s, main, store, item(p, 1)).getStatus()).isEqualTo(403);
        List<Boolean> usable = read(send(get("/api/v1/warehouses"), s, null), "$.warehouses[*].usable");
        assertThat(usable.get(0)).isTrue();
        assertThat(usable.subList(1, usable.size())).containsOnly(false);
        // 只能把货调回主仓库；主仓库照常开单
        assertThat(transfer(s, store, main, item(p, 6)).getStatus()).isEqualTo(201);
        assertThat(sale(s, null, p, 2).getStatus()).isEqualTo(201);
        assertThat(qty(p, main)).isEqualTo(8);
        assertInvariants(s);
    }

    @Test
    void concurrentTransfersAndSalesNeverOversellAWarehouse() throws Exception {
        Session s = registerWithProTenant("wh-cc", "并发仓店");
        long main = defaultWarehouse(s);
        long store = warehouse(s, "门店");
        long p = product(s, "WH-C1", 15);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return transfer(s, main, store, item(p, 6)).getStatus();
            }));
            results.add(pool.submit(() -> {
                start.await();
                return sale(s, main, p, 4).getStatus();
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
        // 主仓库 15：两次调出 6 + 两次卖 4 = 20 > 15，至少有一个失败；剩余不为负
        assertThat(ok).isLessThan(4);
        assertThat(qty(p, main)).isGreaterThanOrEqualTo(0);
        assertThat(qty(p, main) + qty(p, store)).isEqualTo(total(p));
        assertInvariants(s);
    }

    @Test
    void staffSeesQuantitiesButNotValueAndCannotManage() throws Exception {
        Session owner = registerWithProTenant("wh-own", "店员仓店");
        long main = defaultWarehouse(owner);
        long store = warehouse(owner, "门店");
        long p = product(owner, "WH-S1", 10);
        transfer(owner, main, store, item(p, 3));
        Session staff = staffOf(owner);

        MockHttpServletResponse list = send(get("/api/v1/warehouses"), staff, null);
        assertThat((List<?>) read(list, "$.warehouses")).hasSize(2);
        assertThat((Object) read(list, "$.warehouses[0].stockValue")).isNull();
        assertThat((String) read(send(get("/api/v1/warehouses"), owner, null), "$.warehouses[1].stockValue")).isEqualTo("15.00");
        assertThat(sale(staff, store, p, 1).getStatus()).isEqualTo(201);
        assertThat(transfer(staff, main, store, item(p, 1)).getStatus()).isEqualTo(403);
        assertThat(createWarehouse(staff, "店员仓").getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/stock-transfers"), staff, null).getStatus()).isEqualTo(403);
        assertInvariants(owner);
    }

    @Test
    void warehousesAreIsolatedBetweenStores() throws Exception {
        Session x = registerWithProTenant("wh-x", "X 仓店");
        Session y = registerWithProTenant("wh-y", "Y 仓店");
        long xStore = warehouse(x, "X 门店");
        long xMain = defaultWarehouse(x);
        long px = product(x, "WH-I1", 5);
        long tfId = ((Number) read(transfer(x, xMain, xStore, item(px, 2)), "$.id")).longValue();
        long py = product(y, "WH-I1", 5);

        assertThat(sale(y, xStore, py, 1).getStatus()).isEqualTo(404);
        assertThat(transfer(y, defaultWarehouse(y), xStore, item(py, 1)).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/stock-transfers/" + tfId), y, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/warehouses/" + xStore), y, "{\"name\":\"偷改\"}").getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/warehouses/" + xStore + "/stocks").param("productIds", String.valueOf(px)), y, null).getStatus()).isEqualTo(404);
        assertThat((List<?>) read(send(get("/api/v1/warehouses"), y, null), "$.warehouses")).hasSize(1);
        assertInvariants(x);
        assertInvariants(y);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
