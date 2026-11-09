package com.smallbiz.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/**
 * 客户协议价：取价顺序与价格来源、设置 / 修改 / 删除、低于成本提醒、权限、套餐、店铺隔离、导入。
 * 商品：零售 10、批发 9、成本 5。
 */
class CustomerPriceApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    private long product(Session s, String sku, String barcode) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"协议价商品 %s","sku":"%s","barcode":%s,"unit":"个","costPrice":"5","retailPrice":"10","wholesalePrice":"9","lowStockThreshold":0,"initialStock":1000}
                """.formatted(sku, sku, barcode == null ? "null" : "\"" + barcode + "\""));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private long customer(Session s, String name, String phone) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/customers"), s,
                "{\"name\":\"%s\",\"phone\":\"%s\",\"customerType\":\"BUSINESS\",\"priceTier\":\"WHOLESALE\"}".formatted(name, phone));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private MockHttpServletResponse setPrice(Session s, long customerId, long productId, String price) throws Exception {
        return send(put("/api/v1/customers/" + customerId + "/prices/" + productId), s,
                "{\"price\":\"%s\",\"notes\":\"框架协议\"}".formatted(price));
    }

    /** 开单，返回第一行的 [单价, 来源]。unitPrice 为 null 表示不传单价。 */
    private List<String> sale(Session s, Long customerId, String tier, long productId, String unitPrice) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/sales-orders"), s, """
                {"customerId":%s,"priceTier":"%s","items":[{"productId":%d,"quantity":2,"unitPrice":%s}]}
                """.formatted(customerId == null ? "null" : customerId.toString(), tier, productId,
                unitPrice == null ? "null" : "\"" + unitPrice + "\""));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return List.of(read(res, "$.items[0].unitPrice"), read(res, "$.items[0].priceSource"));
    }

    private Session staffOf(Session owner) throws Exception {
        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("cp-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        return new Session(staff.email(), staff.password(),
                read(send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null), "$.accessToken"), null, staff.userId());
    }

    @Test
    void contractPriceComesFirstAndSourceIsRecorded() throws Exception {
        Session s = registerWithProTenant("cp-pri", "协议价店");
        long p = product(s, "CP-1", null);
        long a = customer(s, "客户甲", "13900000001");
        long b = customer(s, "客户乙", "13900000002");
        assertThat(setPrice(s, a, p, "7.5").getStatus()).isEqualTo(200);

        assertThat(sale(s, a, "WHOLESALE", p, null)).containsExactly("7.50", "CONTRACT");
        assertThat(sale(s, b, "WHOLESALE", p, null)).containsExactly("9.00", "WHOLESALE");
        assertThat(sale(s, a, "WHOLESALE", p, "7.50")).containsExactly("7.50", "CONTRACT");   // 前端带出的协议价
        assertThat(sale(s, a, "WHOLESALE", p, "8")).containsExactly("8.00", "MANUAL");        // 手动改价
        assertThat(sale(s, null, "RETAIL", p, null)).containsExactly("10.00", "RETAIL");
        assertThat(sale(s, b, "RETAIL", p, "10")).containsExactly("10.00", "RETAIL");

        Long tenantId = currentTenantId(s, mvc);
        assertThat(jdbcTemplate.queryForList("""
                select i.price_source from sales_order_items i join sales_orders o on i.sales_order_id = o.id
                where o.tenant_id = ? order by o.id
                """, String.class, tenantId)).containsExactly("CONTRACT", "WHOLESALE", "CONTRACT", "MANUAL", "RETAIL", "RETAIL");
        // 金额：7.5×2、9×2、7.5×2、8×2、10×2、10×2
        assertThat(jdbcTemplate.queryForObject("select sum(total_amount) from sales_orders where tenant_id = ?",
                BigDecimal.class, tenantId)).isEqualByComparingTo("104.00");
    }

    @Test
    void setUpdateDeleteAndBelowCostWarning() throws Exception {
        Session s = registerWithProTenant("cp-crud", "维护店");
        long p = product(s, "CP-2", null);
        long a = customer(s, "客户丙", "13900000003");

        MockHttpServletResponse first = setPrice(s, a, p, "7.5");
        assertThat((Boolean) read(first, "$.belowCost")).isFalse();
        MockHttpServletResponse below = setPrice(s, a, p, "4.8");
        assertThat((String) read(below, "$.price")).isEqualTo("4.80");
        assertThat((Boolean) read(below, "$.belowCost")).isTrue();

        List<Map<String, Object>> list = read(send(get("/api/v1/customers/" + a + "/prices"), s, null), "$");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("price", "4.80").containsEntry("wholesalePrice", "9.00").containsEntry("notes", "框架协议");
        assertThat(count("select count(*) from customer_prices where customer_id = ?", a)).isEqualTo(1); // 修改不是新增
        assertThat((List<?>) read(send(get("/api/v1/customers/" + a + "/prices").param("q", "不存在"), s, null), "$")).isEmpty();

        List<Map<String, Object>> byProduct = read(send(get("/api/v1/products/" + p + "/customer-prices"), s, null), "$");
        assertThat(byProduct).extracting(m -> m.get("customerName")).containsExactly("客户丙");

        assertThat(setPrice(s, a, p, "-1").getStatus()).isEqualTo(400);
        assertThat(setPrice(s, a, p, "1.234").getStatus()).isEqualTo(400);

        assertThat(send(delete("/api/v1/customers/" + a + "/prices/" + p), s, null).getStatus()).isEqualTo(204);
        assertThat(send(delete("/api/v1/customers/" + a + "/prices/" + p), s, null).getStatus()).isEqualTo(404);
        assertThat((List<?>) read(send(get("/api/v1/customers/" + a + "/prices"), s, null), "$")).isEmpty();

        // 商品删除后协议价失效（不显示、不带出）
        setPrice(s, a, p, "7");
        assertThat(send(delete("/api/v1/products/" + p), s, null).getStatus()).isEqualTo(204);
        assertThat((List<?>) read(send(get("/api/v1/customers/" + a + "/prices"), s, null), "$")).isEmpty();
    }

    @Test
    void staffGetsQuoteButCannotManage() throws Exception {
        Session owner = registerWithProTenant("cp-own", "店员店");
        long p = product(owner, "CP-3", null);
        long a = customer(owner, "客户丁", "13900000004");
        setPrice(owner, a, p, "7");
        Session staff = staffOf(owner);

        List<Map<String, Object>> quote = read(send(get("/api/v1/customers/" + a + "/prices/quote")
                .param("productIds", String.valueOf(p)), staff, null), "$");
        assertThat(quote).hasSize(1);
        assertThat(quote.get(0)).containsEntry("price", "7.00");
        assertThat(sale(staff, a, "WHOLESALE", p, null)).containsExactly("7.00", "CONTRACT");

        assertThat(send(get("/api/v1/customers/" + a + "/prices"), staff, null).getStatus()).isEqualTo(403);
        assertThat(setPrice(staff, a, p, "6").getStatus()).isEqualTo(403);
        assertThat(send(delete("/api/v1/customers/" + a + "/prices/" + p), staff, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/products/" + p + "/customer-prices"), staff, null).getStatus()).isEqualTo(403);
    }

    @Test
    void needsProAndStopsApplyingAfterDowngrade() throws Exception {
        Session std = registerWithTenant("cp-std", "标准版店");
        long sp = product(std, "CP-4", null);
        long sa = customer(std, "客户戊", "13900000005");
        MockHttpServletResponse denied = setPrice(std, sa, sp, "7");
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat((String) read(denied, "$.details.requiredPlan")).isEqualTo("PRO");

        Session s = registerWithProTenant("cp-down", "降级店");
        long p = product(s, "CP-5", null);
        long a = customer(s, "客户己", "13900000006");
        setPrice(s, a, p, "7");
        Long tenantId = currentTenantId(s, mvc);
        jdbcTemplate.update("update tenants set plan = 'STANDARD' where id = ?", tenantId);

        // 数据保留、可以查看；开单不再使用协议价；不能修改
        assertThat((List<?>) read(send(get("/api/v1/customers/" + a + "/prices"), s, null), "$")).hasSize(1);
        assertThat((List<?>) read(send(get("/api/v1/customers/" + a + "/prices/quote").param("productIds", String.valueOf(p)), s, null), "$")).isEmpty();
        assertThat(sale(s, a, "WHOLESALE", p, null)).containsExactly("9.00", "WHOLESALE");
        assertThat(sale(s, a, "WHOLESALE", p, "7")).containsExactly("7.00", "MANUAL");
        assertThat(setPrice(s, a, p, "6").getStatus()).isEqualTo(403);
        assertThat(count("select count(*) from customer_prices where customer_id = ?", a)).isEqualTo(1);
    }

    @Test
    void storesAreIsolated() throws Exception {
        Session x = registerWithProTenant("cp-x", "X 店");
        Session y = registerWithProTenant("cp-y", "Y 店");
        long px = product(x, "CP-6", null);
        long cx = customer(x, "X 的客户", "13900000007");
        long py = product(y, "CP-6", null);
        setPrice(x, cx, px, "7");

        assertThat(setPrice(y, cx, py, "1").getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/customers/" + cx + "/prices"), y, null).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/customers/" + cx + "/prices/quote").param("productIds", String.valueOf(px)), y, null).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/products/" + px + "/customer-prices"), y, null).getStatus()).isEqualTo(404);
        long cy = customer(y, "Y 的客户", "13900000008");
        assertThat(setPrice(y, cy, px, "1").getStatus()).isEqualTo(404); // 别家的商品
    }

    @Test
    void importMatchesByPhoneOrNameAndSkuOrBarcode() throws Exception {
        Session s = registerWithProTenant("cp-imp", "导入协议价店");
        long p1 = product(s, "IMP-1", null);
        long p2 = product(s, "IMP-2", "6909990001112");
        long a = customer(s, "导入客户甲", "13900000011");
        long b = customer(s, "导入客户乙", "13900000012");
        setPrice(s, a, p1, "8");

        String bad = "客户电话,客户名称,商品SKU,商品条码,协议价*,备注\n"
                + "13900000011,,IMP-1,,7.5,\n"
                + ",导入客户乙,,6909990001112,6.8,批发合同\n"
                + "13999999999,,IMP-1,,7,\n"
                + "13900000011,,NOPE,,7,\n"
                + "13900000011,,IMP-1,,7.2,\n";
        MockHttpServletResponse check = upload(s, bad, "UPSERT", true);
        List<Map<String, Object>> errors = read(check, "$.errors");
        assertThat(errors).extracting(e -> e.get("row") + ":" + e.get("column"))
                .containsExactlyInAnyOrder("4:客户电话", "5:商品SKU", "6:null");

        String good = "客户电话,客户名称,商品SKU,商品条码,协议价*,备注\n"
                + "13900000011,,IMP-1,,7.5,\n"
                + ",导入客户乙,,6909990001112,6.8,批发合同\n";
        MockHttpServletResponse skip = upload(s, good, "CREATE_ONLY", false);
        assertThat((Integer) read(skip, "$.created")).isEqualTo(1);
        assertThat((Integer) read(skip, "$.skipped")).isEqualTo(1);
        assertThat(price(a, p1)).isEqualByComparingTo("8");
        assertThat(price(b, p2)).isEqualByComparingTo("6.8");

        MockHttpServletResponse up = upload(s, good, "UPSERT", false);
        assertThat((Integer) read(up, "$.updated")).isEqualTo(2);
        assertThat(price(a, p1)).isEqualByComparingTo("7.5");

        // 标准版不能导入协议价
        Session std = registerWithTenant("cp-imp-std", "标准版导入店");
        assertThat(upload(std, good, "UPSERT", true).getStatus()).isEqualTo(403);
    }

    private BigDecimal price(long customerId, long productId) {
        return jdbcTemplate.queryForObject("select price from customer_prices where customer_id = ? and product_id = ?",
                BigDecimal.class, customerId, productId);
    }

    private MockHttpServletResponse upload(Session s, String csv, String mode, boolean dryRun) throws Exception {
        MockHttpServletResponse res = mvc.perform(multipart("/api/v1/imports/customer-prices")
                .file(new MockMultipartFile("file", "prices.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("mode", mode).param("dryRun", String.valueOf(dryRun))
                .header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
