package com.smallbiz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantContext;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

class InventoryApiTest extends IntegrationTest {

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    TenantMemberRepository members;

    @Autowired
    TransactionTemplate tx;

    private Long product(Session s, String name, String cost, int initial, int threshold) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"%s","unit":"件","costPrice":"%s","retailPrice":"99","lowStockThreshold":%d,"initialStock":%d}
                """.formatted(name, cost, threshold, initial));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private MockHttpServletResponse stockIn(Session s, Long id, int qty, String unitCost) throws Exception {
        return send(post("/api/v1/inventory/stock-in"), s, """
                {"productId":%d,"quantity":%d,"unitCost":%s,"reason":"补货"}
                """.formatted(id, qty, unitCost == null ? "null" : "\"" + unitCost + "\""));
    }

    private MockHttpServletResponse stockOut(Session s, Long id, int qty) throws Exception {
        return send(post("/api/v1/inventory/stock-out"), s,
                "{\"productId\":%d,\"quantity\":%d,\"reason\":\"损耗\"}".formatted(id, qty));
    }

    private MockHttpServletResponse adjust(Session s, Long id, int actual) throws Exception {
        return send(post("/api/v1/inventory/adjust"), s,
                "{\"productId\":%d,\"actualQty\":%d,\"reason\":\"月底盘点\"}".formatted(id, actual));
    }

    // ---------------- 入库 ----------------

    @Test
    void stockInIncreasesStockAndUpdatesWeightedAverageCost() throws Exception {
        Session s = registerWithTenant("inv-in", "入库店");
        Long id = product(s, "耳机", "10.00", 10, 2);

        MockHttpServletResponse res = stockIn(s, id, 30, "14.00");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        assertThat((Integer) read(res, "$.stockQty")).isEqualTo(40);
        // (10×10 + 30×14) / 40 = 13.00
        assertThat((String) read(res, "$.costPrice")).isEqualTo("13.00");

        // 不填单价：只加库存，不改成本
        MockHttpServletResponse noCost = stockIn(s, id, 5, null);
        assertThat((Integer) read(noCost, "$.stockQty")).isEqualTo(45);
        assertThat((String) read(noCost, "$.costPrice")).isEqualTo("13.00");
        assertConsistent(s, id);
    }

    @Test
    void weightedAverageWhenStockIsZeroUsesIncomingCost() {
        assertThat(InventoryService.weightedAverageCost(0, new BigDecimal("50"), 10, new BigDecimal("8.5")))
                .isEqualByComparingTo("8.50");
        assertThat(InventoryService.weightedAverageCost(-3, new BigDecimal("50"), 10, new BigDecimal("8.5")))
                .isEqualByComparingTo("8.50");
        // 3×1 + 1×2 = 5 / 4 = 1.25
        assertThat(InventoryService.weightedAverageCost(3, new BigDecimal("1"), 1, new BigDecimal("2")))
                .isEqualByComparingTo("1.25");
    }

    // ---------------- 出库 ----------------

    @Test
    void stockOutRejectsInsufficientStock_unlessNegativeAllowed() throws Exception {
        Session s = registerWithTenant("inv-out", "出库店");
        Long id = product(s, "充电宝", "50", 5, 1);

        MockHttpServletResponse tooMany = stockOut(s, id, 6);
        assertThat(tooMany.getStatus()).isEqualTo(409);
        assertThat((String) read(tooMany, "$.code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat((Integer) read(tooMany, "$.details.available")).isEqualTo(5);
        assertThat((Integer) read(tooMany, "$.details.requested")).isEqualTo(6);

        assertThat((Integer) read(stockOut(s, id, 5), "$.stockQty")).isZero();

        // 店铺开启负库存后允许
        send(put("/api/v1/tenant"), s, """
                {"name":"出库店","businessType":"RETAIL","timezone":"Asia/Shanghai","allowNegativeStock":true}
                """);
        MockHttpServletResponse negative = stockOut(s, id, 2);
        assertThat(negative.getStatus()).isEqualTo(201);
        assertThat((Integer) read(negative, "$.stockQty")).isEqualTo(-2);
        assertConsistent(s, id);
    }

    @Test
    void stockOutRequiresReasonAndPositiveQuantity() throws Exception {
        Session s = registerWithTenant("inv-val", "校验店");
        Long id = product(s, "x", "1", 5, 0);
        MockHttpServletResponse res = send(post("/api/v1/inventory/stock-out"), s,
                "{\"productId\":%d,\"quantity\":0,\"reason\":\" \"}".formatted(id));
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat((String) read(res, "$.details.fields.quantity")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.reason")).isNotBlank();
    }

    // ---------------- 盘点 ----------------

    @Test
    void adjustSetsActualQuantityAndRecordsDelta() throws Exception {
        Session s = registerWithTenant("inv-adj", "盘点店");
        Long id = product(s, "手机壳", "3", 20, 5);

        MockHttpServletResponse down = adjust(s, id, 17);
        assertThat((Integer) read(down, "$.qtyChange")).isEqualTo(-3);
        assertThat((Integer) read(down, "$.stockQty")).isEqualTo(17);

        MockHttpServletResponse up = adjust(s, id, 25);
        assertThat((Integer) read(up, "$.qtyChange")).isEqualTo(8);

        MockHttpServletResponse same = adjust(s, id, 25);
        assertThat(same.getStatus()).isEqualTo(400);
        assertThat((String) read(same, "$.code")).isEqualTo("NO_CHANGE");

        MockHttpServletResponse negative = adjust(s, id, -1);
        assertThat(negative.getStatus()).isEqualTo(400);
        assertConsistent(s, id);
    }

    // ---------------- 查询 ----------------

    @Test
    void movementsCanBeFilteredByProductTypeAndDate() throws Exception {
        Session s = registerWithTenant("inv-mv", "流水店");
        Long a = product(s, "商品A", "1", 10, 0);
        Long b = product(s, "商品B", "1", 10, 0);
        stockIn(s, a, 5, null);
        stockOut(s, a, 2);
        adjust(s, b, 8);

        MockHttpServletResponse all = send(get("/api/v1/inventory/movements"), s, null);
        assertThat((Integer) read(all, "$.totalItems")).isEqualTo(5); // 2 初始 + 入 + 出 + 盘点
        assertThat((String) read(all, "$.items[0].productName")).isEqualTo("商品B"); // 最新在前
        assertThat((String) read(all, "$.items[0].operatorName")).isNotBlank();

        assertThat((Integer) read(send(get("/api/v1/inventory/movements?productId=" + a), s, null), "$.totalItems"))
                .isEqualTo(3);
        assertThat((Integer) read(send(get("/api/v1/inventory/movements?type=STOCK_OUT"), s, null), "$.totalItems"))
                .isEqualTo(1);

        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(2).toString();
        assertThat((Integer) read(send(get("/api/v1/inventory/movements?from=" + tomorrow), s, null), "$.totalItems"))
                .isZero();
        assertThat((Integer) read(send(get("/api/v1/inventory/movements?from=2000-01-01&to=" + tomorrow), s, null),
                "$.totalItems")).isEqualTo(5);
        assertThat(send(get("/api/v1/inventory/movements?from=" + tomorrow + "&to=" + today), s, null).getStatus())
                .isEqualTo(400);
    }

    @Test
    void summaryAndLowStockReflectRealData() throws Exception {
        Session s = registerWithTenant("inv-sum", "汇总店");
        product(s, "充足", "2.50", 100, 10);   // 价值 250
        product(s, "偏低", "10", 3, 5);        // 价值 30，低库存
        product(s, "缺货", "7", 0, 0);         // 低库存 + 缺货

        MockHttpServletResponse sum = send(get("/api/v1/inventory/summary"), s, null);
        assertThat((Integer) read(sum, "$.productCount")).isEqualTo(3);
        assertThat((Integer) read(sum, "$.totalQty")).isEqualTo(103);
        assertThat((String) read(sum, "$.inventoryValue")).isEqualTo("280.00");
        assertThat((Integer) read(sum, "$.lowStockCount")).isEqualTo(2);
        assertThat((Integer) read(sum, "$.outOfStockCount")).isEqualTo(1);

        MockHttpServletResponse low = send(get("/api/v1/inventory/low-stock"), s, null);
        assertThat((Integer) read(low, "$.count")).isEqualTo(2);
        assertThat((List<String>) read(low, "$.items[*].name")).containsExactly("缺货", "偏低");
    }

    // ---------------- 权限与隔离 ----------------

    @Test
    void staffCanViewButNotChangeStock_andSeesNoValue() throws Exception {
        Session owner = registerWithTenant("inv-own", "权限店");
        Long tenantId = currentTenantId(owner, mvc);
        Long id = product(owner, "商品", "5", 10, 0);
        Session staff = register("inv-staff");
        allowStaff(tenantId);
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session staffIn = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        assertThat(stockIn(staffIn, id, 1, null).getStatus()).isEqualTo(403);
        assertThat(stockOut(staffIn, id, 1).getStatus()).isEqualTo(403);
        assertThat(adjust(staffIn, id, 1).getStatus()).isEqualTo(403);

        assertThat((Object) read(send(get("/api/v1/inventory/summary"), staffIn, null), "$.inventoryValue")).isNull();
        assertThat((Object) read(send(get("/api/v1/inventory/movements"), staffIn, null), "$.items[0].unitCost"))
                .isNull();
    }

    @Test
    void anotherTenantCannotTouchOrSeeStock() throws Exception {
        Session a = registerWithTenant("inv-iso-a", "库存A");
        Session b = registerWithTenant("inv-iso-b", "库存B");
        Long idA = product(a, "A 的商品", "9", 10, 20);

        assertThat(stockIn(b, idA, 100, "1").getStatus()).isEqualTo(404);
        assertThat(stockOut(b, idA, 1).getStatus()).isEqualTo(404);
        assertThat(adjust(b, idA, 0).getStatus()).isEqualTo(404);

        assertThat((Integer) read(send(get("/api/v1/inventory/movements"), b, null), "$.totalItems")).isZero();
        assertThat((Integer) read(send(get("/api/v1/inventory/movements?productId=" + idA), b, null), "$.totalItems"))
                .isZero();
        assertThat((Integer) read(send(get("/api/v1/inventory/summary"), b, null), "$.productCount")).isZero();
        assertThat((Integer) read(send(get("/api/v1/inventory/low-stock"), b, null), "$.count")).isZero();

        // A 的库存和成本没有被改动
        MockHttpServletResponse still = send(get("/api/v1/products/" + idA), a, null);
        assertThat((Integer) read(still, "$.stockQty")).isEqualTo(10);
        assertThat((String) read(still, "$.costPrice")).isEqualTo("9.00");
        assertConsistent(a, idA);
    }

    private void assertConsistent(Session s, Long productId) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> {
            Product p = products.findById(productId).orElseThrow();
            assertThat((long) p.getStockQty()).isEqualTo(movements.sumChangeByProductId(productId));
        }));
    }
}
