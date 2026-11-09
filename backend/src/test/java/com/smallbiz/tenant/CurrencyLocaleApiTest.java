package com.smallbiz.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.smallbiz.support.IntegrationTest;

/** 店铺币种（开店选择、有业务数据后锁定、老店可修正一次）、用户界面语言、英文表格。 */
class CurrencyLocaleApiTest extends IntegrationTest {

    private Session store(String prefix, String currency) throws Exception {
        Session s = register(prefix);
        MockHttpServletResponse res = send(post("/api/v1/tenants"), s,
                "{\"name\":\"%s 店\",\"businessType\":\"RETAIL\"%s}".formatted(prefix,
                        currency == null ? "" : ",\"currency\":\"" + currency + "\""));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return new Session(s.email(), s.password(), read(res, "$.accessToken"), refreshCookie(res), s.userId());
    }

    private MockHttpServletResponse updateCurrency(Session s, String currency) throws Exception {
        return send(put("/api/v1/tenant"), s, """
                {"name":"改名店","businessType":"RETAIL","timezone":"Asia/Kuala_Lumpur","contactPhone":"","address":"","allowNegativeStock":false,"currency":"%s"}
                """.formatted(currency));
    }

    private void sell(Session s) throws Exception {
        MockHttpServletResponse p = send(post("/api/v1/products"), s, """
                {"name":"币种商品","unit":"个","costPrice":"1","retailPrice":"2","lowStockThreshold":0,"initialStock":5}
                """);
        long id = ((Number) read(p, "$.id")).longValue();
        assertThat(send(post("/api/v1/sales-orders"), s, """
                {"priceTier":"RETAIL","items":[{"productId":%d,"quantity":1}]}
                """.formatted(id)).getStatus()).isEqualTo(201);
    }

    @Test
    void storeCurrencyIsChosenAtCreationAndLockedOnceThereIsBusinessData() throws Exception {
        Session s = store("cur-my", "MYR");
        MockHttpServletResponse t = send(get("/api/v1/tenant"), s, null);
        assertThat((String) read(t, "$.currency")).isEqualTo("MYR");
        assertThat((String) read(t, "$.timezone")).isEqualTo("Asia/Kuala_Lumpur");
        assertThat((Boolean) read(t, "$.currencyConfirmed")).isTrue();
        assertThat((Boolean) read(t, "$.currencyLocked")).isFalse();
        assertThat((String) read(send(get("/api/v1/auth/me"), s, null), "$.currentTenant.currency")).isEqualTo("MYR");

        // 还没有业务数据：可以改
        assertThat((String) read(updateCurrency(s, "SGD"), "$.currency")).isEqualTo("SGD");
        sell(s);
        MockHttpServletResponse locked = updateCurrency(s, "MYR");
        assertThat(locked.getStatus()).isEqualTo(409);
        assertThat((String) read(locked, "$.code")).isEqualTo("CURRENCY_LOCKED");
        assertThat((Boolean) read(send(get("/api/v1/tenant"), s, null), "$.currencyLocked")).isTrue();
        // 不改币种时照常保存其他资料
        assertThat(updateCurrency(s, "SGD").getStatus()).isEqualTo(200);

        MockHttpServletResponse bad = send(post("/api/v1/tenants"), register("cur-bad"),
                "{\"name\":\"坏币种\",\"businessType\":\"RETAIL\",\"currency\":\"EUR\"}");
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat((String) read(bad, "$.code")).isEqualTo("UNSUPPORTED_CURRENCY");
    }

    @Test
    void legacyStoreCanCorrectItsCurrencyOnceEvenWithSales() throws Exception {
        Session s = store("cur-old", null); // 以前开的店：默认人民币、未确认
        sell(s);
        MockHttpServletResponse t = send(get("/api/v1/tenant"), s, null);
        assertThat((String) read(t, "$.currency")).isEqualTo("CNY");
        assertThat((Boolean) read(t, "$.currencyConfirmed")).isFalse();
        assertThat((Boolean) read(t, "$.currencyLocked")).isFalse();

        assertThat((String) read(updateCurrency(s, "MYR"), "$.currency")).isEqualTo("MYR");
        assertThat(updateCurrency(s, "CNY").getStatus()).isEqualTo(409);
        // 金额本身不变（只改显示的币种）
        assertThat(jdbcTemplate.queryForObject("select total_amount from sales_orders where tenant_id = ?",
                java.math.BigDecimal.class, currentTenantId(s, mvc))).isEqualByComparingTo("2");
    }

    @Test
    void userLocaleIsSavedOnTheAccount() throws Exception {
        Session s = register("loc");
        assertThat((Object) read(send(get("/api/v1/auth/me"), s, null), "$.user.locale")).isNull();
        assertThat((String) read(send(put("/api/v1/auth/me/locale"), s, "{\"locale\":\"en\"}"), "$.locale")).isEqualTo("en");
        assertThat((String) read(send(get("/api/v1/auth/me"), s, null), "$.user.locale")).isEqualTo("en");
        assertThat(send(put("/api/v1/auth/me/locale"), s, "{\"locale\":\"fr\"}").getStatus()).isEqualTo(400);
        // 登录响应里也有
        MockHttpServletResponse login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(s.email(), s.password()))).andReturn().getResponse();
        assertThat((String) read(login, "$.user.locale")).isEqualTo("en");
    }

    @Test
    void spreadsheetsAreInEnglishWhenAskedAndEnglishHeadersCanBeImported() throws Exception {
        Session s = registerWithProTenant("cur-xl", "英文表格店");
        try (Workbook wb = book(send(get("/api/v1/imports/products/template").param("lang", "en"), s, null))) {
            Row h = wb.getSheetAt(0).getRow(0);
            assertThat(h.getCell(0).getStringCellValue()).isEqualTo("Product name*");
            assertThat(h.getCell(2).getStringCellValue()).isEqualTo("Retail price*");
            assertThat(wb.getSheetName(1)).isEqualTo("Instructions");
        }

        String csv = "Product name*,Unit*,Retail price*,Cost price*,SKU,Status,Opening stock\nEnglish cable,pc,9.9,4,EN-1,Active,3\n";
        MockHttpServletResponse imp = mvc.perform(multipart("/api/v1/imports/products")
                .file(new MockMultipartFile("file", "en.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "false").header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
        imp.setCharacterEncoding("UTF-8");
        assertThat(imp.getStatus()).as(imp.getContentAsString()).isEqualTo(200);
        assertThat((Boolean) read(imp, "$.imported")).as(imp.getContentAsString()).isTrue();

        MockHttpServletResponse ex = send(get("/api/v1/exports/products").param("lang", "en"), s, null);
        assertThat(ex.getHeader("Content-Disposition")).contains("Products_");
        try (Workbook wb = book(ex)) {
            assertThat(wb.getSheetName(0)).isEqualTo("Products");
            Row h = wb.getSheetAt(0).getRow(0);
            assertThat(h.getCell(0).getStringCellValue()).isEqualTo("Product name");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(11).getStringCellValue()).isEqualTo("Active");
        }
        // 不带 lang 仍是中文
        try (Workbook wb = book(send(get("/api/v1/exports/products"), s, null))) {
            assertThat(wb.getSheetName(0)).isEqualTo("商品");
        }
        MockHttpServletResponse report = send(get("/api/v1/reports/sales/export").param("lang", "en"), s, null);
        assertThat(report.getContentAsString(StandardCharsets.UTF_8)).contains("Date,Orders,Sales,Cost,Gross profit");
    }

    private static Workbook book(MockHttpServletResponse res) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()));
    }
}
