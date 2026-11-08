package com.smallbiz.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/**
 * 报表：用一组可手算的数据核对每一个数字。
 * 商品 A 成本 4.50 / 零售 15；商品 B 成本 10 / 零售 30。
 * 销售①：X 买 A×2 = 30.00（成本 9.00）
 * 销售②：Y 买 B×1 + A×1，优惠 5 → 40.00（成本 14.50）
 * 销售③：Y 买 A×10，随后取消 → 不计入
 * 采购：A×10 @5 = 50.00，已收货
 * 预期：销售额 70.00，成本 23.50，毛利 46.50，采购 50.00
 */
class ReportApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    record Fixture(Session s, Long a, Long b, Long x, Long y) {
    }

    private Long id(MockHttpServletResponse res) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isBetween(200, 201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Fixture fixture(String prefix) throws Exception {
        Session s = registerWithTenant(prefix, prefix + "店");
        Long cat = id(send(post("/api/v1/categories"), s, "{\"name\":\"配件\"}"));
        Long a = id(send(post("/api/v1/products"), s, """
                {"name":"商品A","categoryId":%d,"unit":"个","costPrice":"4.50","retailPrice":"15","lowStockThreshold":0,"initialStock":100}
                """.formatted(cat)));
        Long b = id(send(post("/api/v1/products"), s, """
                {"name":"商品B","unit":"个","costPrice":"10","retailPrice":"30","lowStockThreshold":0,"initialStock":10}
                """));
        Long x = id(send(post("/api/v1/customers"), s, "{\"name\":\"客户X\",\"customerType\":\"INDIVIDUAL\",\"priceTier\":\"RETAIL\"}"));
        // 名字以 = 开头：验证 CSV 公式注入防护
        Long y = id(send(post("/api/v1/customers"), s, "{\"name\":\"=1+1 客户Y\",\"customerType\":\"BUSINESS\",\"priceTier\":\"RETAIL\"}"));

        id(send(post("/api/v1/sales-orders"), s, """
                {"customerId":%d,"priceTier":"RETAIL","items":[{"productId":%d,"quantity":2}]}
                """.formatted(x, a)));
        id(send(post("/api/v1/sales-orders"), s, """
                {"customerId":%d,"priceTier":"RETAIL","discountAmount":"5","items":[{"productId":%d,"quantity":1},{"productId":%d,"quantity":1}]}
                """.formatted(y, b, a)));
        Long cancelled = id(send(post("/api/v1/sales-orders"), s, """
                {"customerId":%d,"priceTier":"RETAIL","items":[{"productId":%d,"quantity":10}]}
                """.formatted(y, a)));
        send(post("/api/v1/sales-orders/" + cancelled + "/cancel"), s, "{}");

        Long sup = id(send(post("/api/v1/suppliers"), s, "{\"name\":\"供应商\"}"));
        Long po = id(send(post("/api/v1/purchase-orders"), s, """
                {"supplierId":%d,"submit":true,"items":[{"productId":%d,"quantity":10,"unitCost":"5"}]}
                """.formatted(sup, a)));
        send(post("/api/v1/purchase-orders/" + po + "/receive"), s, null);
        return new Fixture(s, a, b, x, y);
    }

    @Test
    void dashboardMatchesHandCalculation() throws Exception {
        Fixture f = fixture("rp-dash");
        MockHttpServletResponse d = send(get("/api/v1/dashboard/summary"), f.s(), null);
        assertThat((String) read(d, "$.todaySales")).isEqualTo("70.00");
        assertThat((Integer) read(d, "$.todayOrders")).isEqualTo(2);
        assertThat((String) read(d, "$.monthSales")).isEqualTo("70.00");
        assertThat((Integer) read(d, "$.monthOrders")).isEqualTo(2);
        assertThat((String) read(d, "$.monthGrossProfit")).isEqualTo("46.50");
        assertThat((String) read(d, "$.monthPurchases")).isEqualTo("50.00");

        MockHttpServletResponse trend = send(get("/api/v1/dashboard/sales-trend?days=7"), f.s(), null);
        List<String> amounts = read(trend, "$.points[*].amount");
        assertThat(amounts).hasSize(7).containsExactly("0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "70.00");
        assertThat((String) read(trend, "$.total.grossProfit")).isEqualTo("46.50");
    }

    @Test
    void reportsMatchHandCalculation() throws Exception {
        Fixture f = fixture("rp-rep");
        String today = (String) read(send(get("/api/v1/dashboard/summary"), f.s(), null), "$.today");

        MockHttpServletResponse month = send(get("/api/v1/reports/sales?groupBy=MONTH&from=" + today + "&to=" + today), f.s(), null);
        assertThat((List<?>) read(month, "$.points")).hasSize(1);
        assertThat((String) read(month, "$.points[0].period")).isEqualTo(today.substring(0, 7));
        assertThat((String) read(month, "$.total.amount")).isEqualTo("70.00");
        assertThat((String) read(month, "$.total.cost")).isEqualTo("23.50");

        MockHttpServletResponse top = send(get("/api/v1/reports/top-products"), f.s(), null);
        assertThat((List<String>) read(top, "$[*].productName")).containsExactly("商品A", "商品B");
        assertThat((Integer) read(top, "$[0].quantity")).isEqualTo(3);           // 取消的 10 个不计
        assertThat((String) read(top, "$[0].amount")).isEqualTo("45.00");         // 30 + 15
        assertThat((String) read(top, "$[0].grossProfit")).isEqualTo("31.50");    // 45 − 3×4.50
        assertThat((String) read(top, "$[1].grossProfit")).isEqualTo("20.00");

        MockHttpServletResponse customers = send(get("/api/v1/reports/top-customers"), f.s(), null);
        assertThat((List<String>) read(customers, "$[*].customerName")).containsExactly("=1+1 客户Y", "客户X");
        assertThat((String) read(customers, "$[0].amount")).isEqualTo("40.00");
        assertThat((Integer) read(customers, "$[0].orderCount")).isEqualTo(1);

        MockHttpServletResponse purchases = send(get("/api/v1/reports/purchases"), f.s(), null);
        assertThat((String) read(purchases, "$.total.amount")).isEqualTo("50.00");
        assertThat((Integer) read(purchases, "$.total.orderCount")).isEqualTo(1);

        // 库存：A = 100 − 2 − 1 + 10 = 107，成本 (97×4.50 + 10×5)/107 = 4.55 → 486.85；B = 9 × 10 = 90
        MockHttpServletResponse inv = send(get("/api/v1/reports/inventory"), f.s(), null);
        assertThat((List<String>) read(inv, "$[*].categoryName")).containsExactly("配件", "未分类");
        assertThat((Integer) read(inv, "$[0].quantity")).isEqualTo(107);
        assertThat((String) read(inv, "$[0].value")).isEqualTo("486.85");
        assertThat((String) read(inv, "$[1].value")).isEqualTo("90.00");
    }

    @Test
    void csvExportOpensInExcelAndBlocksFormulaInjection() throws Exception {
        Fixture f = fixture("rp-csv");
        MockHttpServletResponse csv = send(get("/api/v1/reports/top-customers/export"), f.s(), null);
        assertThat(csv.getStatus()).isEqualTo(200);
        assertThat(csv.getContentType()).startsWith("text/csv");
        assertThat(csv.getHeader("Content-Disposition")).contains("attachment");
        byte[] bytes = csv.getContentAsByteArray();
        assertThat(new String(bytes, 0, 3, StandardCharsets.ISO_8859_1)).isEqualTo("ï»¿"); // UTF-8 BOM
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).contains("排名,客户,订单数,消费金额").contains("'=1+1 客户Y").doesNotContain(",=1+1");

        MockHttpServletResponse sales = send(get("/api/v1/reports/sales/export"), f.s(), null);
        assertThat(new String(sales.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("合计,2,70.00,23.50,46.50");
    }

    @Test
    void todayFollowsStoreTimezone() throws Exception {
        Session s = registerWithTenant("rp-tz", "时区店");
        send(put("/api/v1/tenant"), s, """
                {"name":"时区店","businessType":"RETAIL","timezone":"Pacific/Kiritimati","allowNegativeStock":false}
                """);
        MockHttpServletResponse d = send(get("/api/v1/dashboard/summary"), s, null);
        assertThat((String) read(d, "$.today")).isEqualTo(LocalDate.now(ZoneId.of("Pacific/Kiritimati")).toString());
    }

    @Test
    void validatesDateRange() throws Exception {
        Session s = registerWithTenant("rp-val", "校验店");
        assertThat((String) read(send(get("/api/v1/reports/sales?from=2026-02-01&to=2026-01-01"), s, null), "$.code"))
                .isEqualTo("INVALID_DATE_RANGE");
        assertThat((String) read(send(get("/api/v1/reports/sales?from=2024-01-01&to=2026-01-01"), s, null), "$.code"))
                .isEqualTo("RANGE_TOO_LONG");
    }

    @Test
    void staffSeesSalesOnly_andReportsAreIsolated() throws Exception {
        Fixture f = fixture("rp-iso");
        Long tenantId = currentTenantId(f.s(), mvc);
        Session staff = register("rp-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session st = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        MockHttpServletResponse d = send(get("/api/v1/dashboard/summary"), st, null);
        assertThat((String) read(d, "$.todaySales")).isEqualTo("70.00");
        assertThat((Object) read(d, "$.monthGrossProfit")).isNull();
        assertThat((Object) read(d, "$.monthPurchases")).isNull();
        assertThat((Object) read(send(get("/api/v1/dashboard/sales-trend"), st, null), "$.total.cost")).isNull();
        assertThat(send(get("/api/v1/reports/sales"), st, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/reports/top-products/export"), st, null).getStatus()).isEqualTo(403);

        // 另一家店铺看到的全是 0
        Session other = registerWithTenant("rp-other", "别家店");
        MockHttpServletResponse od = send(get("/api/v1/dashboard/summary"), other, null);
        assertThat((String) read(od, "$.monthSales")).isEqualTo("0.00");
        assertThat((String) read(od, "$.monthPurchases")).isEqualTo("0.00");
        assertThat((List<?>) read(send(get("/api/v1/reports/top-products"), other, null), "$")).isEmpty();
        assertThat((List<?>) read(send(get("/api/v1/reports/top-customers"), other, null), "$")).isEmpty();
        assertThat((List<?>) read(send(get("/api/v1/reports/inventory"), other, null), "$")).isEmpty();
    }
}
