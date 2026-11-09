package com.smallbiz.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/**
 * Excel 导入导出：模板、预检、整批回滚、重复处理、免费版上限、CSV 编码、坏文件、权限、导出读回核对、店铺隔离。
 */
class ImportExportApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    static final List<String> PRODUCT_HEADER = List.of("商品名称*", "单位*", "零售价*", "进货成本*", "SKU", "条码", "分类",
            "批发价", "库存预警值", "初始库存", "状态", "描述");

    // ---------------- 辅助 ----------------

    static byte[] xlsx(List<String> header, List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("数据");
            Row h = s.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                h.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    Object v = rows.get(r).get(c);
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 商品行：名称、单位、零售价、成本、SKU、条码、分类、批发价、预警值、初始库存、状态、描述。 */
    static List<Object> product(String name, Object retail, Object cost, String sku, String category, Object stock) {
        List<Object> row = new ArrayList<>(List.of(name, "个", retail, cost));
        row.add(sku);
        row.add(null);
        row.add(category);
        row.add(null);
        row.add(null);
        row.add(stock);
        return row;
    }

    private MockHttpServletResponse upload(Session s, String type, String filename, byte[] bytes, String mode,
            boolean dryRun) throws Exception {
        MockHttpServletResponse res = mvc.perform(multipart("/api/v1/imports/" + type)
                .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                .param("mode", mode).param("dryRun", String.valueOf(dryRun))
                .header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private static Workbook book(MockHttpServletResponse res) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        assertThat(res.getContentType()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()));
    }

    private void assertStockMatchesMovements(Long tenantId) {
        assertThat(count("""
                select count(*) from products p where p.tenant_id = ? and p.stock_qty <>
                  (select coalesce(sum(m.qty_change), 0) from stock_movements m where m.product_id = p.id)
                """, tenantId)).isZero();
    }

    // ---------------- 模板 ----------------

    @Test
    void templateHasHeadersAndInstructions() throws Exception {
        Session s = registerWithFreeTenant("ix-tpl", "模板店");
        try (Workbook wb = book(send(get("/api/v1/imports/products/template"), s, null))) {
            Row h = wb.getSheetAt(0).getRow(0);
            assertThat(h.getCell(0).getStringCellValue()).isEqualTo("商品名称*");
            assertThat(h.getCell(4).getStringCellValue()).isEqualTo("SKU");
            assertThat(wb.getSheet("填写说明").getLastRowNum()).isEqualTo(12);
        }
        // 免费版：客户需要标准版
        assertThat(send(get("/api/v1/imports/customers/template"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/imports/orders/template"), s, null).getStatus()).isEqualTo(404);
    }

    // ---------------- 商品导入 ----------------

    @Test
    void dryRunThenImportProductsWithCategoriesAndStock() throws Exception {
        Session s = registerWithFreeTenant("ix-p", "导入店");
        Long tenantId = currentTenantId(s, mvc);
        byte[] file = xlsx(PRODUCT_HEADER, List.of(
                product("数据线", 19.9, 8.5, "CABLE-1", "数码 / 数据线", 10),
                product("充电头", "29.00", "12", null, "数码 / 充电", null),
                product("手机壳", "¥15", "5.5", "CASE-1", "配件", 5)));

        MockHttpServletResponse dry = upload(s, "products", "商品.xlsx", file, "CREATE_ONLY", true);
        assertThat(dry.getStatus()).as(dry.getContentAsString()).isEqualTo(200);
        assertThat((Integer) read(dry, "$.created")).isEqualTo(3);
        assertThat((Boolean) read(dry, "$.imported")).isFalse();
        assertThat((List<String>) read(dry, "$.newCategories")).containsExactly("数码 / 数据线", "数码 / 充电", "配件");
        assertThat(count("select count(*) from products where tenant_id = ?", tenantId)).isZero();
        assertThat(count("select count(*) from categories where tenant_id = ?", tenantId)).isZero();

        MockHttpServletResponse done = upload(s, "products", "商品.xlsx", file, "CREATE_ONLY", false);
        assertThat(done.getStatus()).as(done.getContentAsString()).isEqualTo(200);
        assertThat((Boolean) read(done, "$.imported")).isTrue();
        assertThat(count("select count(*) from products where tenant_id = ?", tenantId)).isEqualTo(3);
        // 数码（一级）下有 数据线、充电 两个二级分类；配件为一级
        assertThat(count("select count(*) from categories where tenant_id = ? and parent_id is null", tenantId)).isEqualTo(2);
        assertThat(count("""
                select count(*) from categories c join categories p on c.parent_id = p.id
                where c.tenant_id = ? and p.name = '数码'
                """, tenantId)).isEqualTo(2);
        Map<String, Object> cable = jdbcTemplate.queryForMap(
                "select retail_price, cost_price, stock_qty from products where tenant_id = ? and sku = 'CABLE-1'", tenantId);
        assertThat((BigDecimal) cable.get("retail_price")).isEqualByComparingTo("19.90");
        assertThat((BigDecimal) cable.get("cost_price")).isEqualByComparingTo("8.50");
        assertThat(cable.get("stock_qty")).isEqualTo(10);
        assertThat(count("""
                select count(*) from stock_movements m join products p on m.product_id = p.id
                where p.tenant_id = ? and m.type = 'INITIAL'
                """, tenantId)).isEqualTo(2);
        // 没填 SKU 的自动生成
        assertThat(count("select count(*) from products where tenant_id = ? and name = '充电头' and sku like 'SKU%'", tenantId)).isEqualTo(1);
        assertStockMatchesMovements(tenantId);
    }

    @Test
    void anyErrorBlocksTheWholeBatch() throws Exception {
        Session s = registerWithFreeTenant("ix-err", "错误店");
        Long tenantId = currentTenantId(s, mvc);
        List<Object> noUnit = product("无单位", 10, 5, "B-2", null, null);
        noUnit.set(1, null);
        List<Object> badStatus = product("状态错", 10, 5, "B-4", null, null);
        badStatus.add("热卖");
        byte[] file = xlsx(PRODUCT_HEADER, List.of(
                product("好商品", 10, 5, "OK-1", null, 3),
                product("价格错", "abc", 5, "B-1", null, null),
                noUnit,
                product("重复", 10, 5, "OK-1", null, null),
                badStatus,
                product("小数库存", 10, 5, "B-5", null, 1.5),
                product("三位小数", "1.999", 5, "B-6", null, null)));

        MockHttpServletResponse res = upload(s, "products", "p.xlsx", file, "CREATE_ONLY", false);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((Boolean) read(res, "$.imported")).isFalse();
        List<Map<String, Object>> errors = read(res, "$.errors");
        assertThat(errors).extracting(e -> e.get("row") + ":" + e.get("column")).containsExactlyInAnyOrder(
                "3:零售价", "4:单位", "5:SKU", "6:状态", "7:初始库存", "8:零售价");
        assertThat(errors.stream().filter(e -> (Integer) e.get("row") == 5).findFirst().orElseThrow().get("message"))
                .asString().contains("与第 2 行重复");
        assertThat(count("select count(*) from products where tenant_id = ?", tenantId)).isZero();
        assertThat(count("select count(*) from stock_movements m join products p on m.product_id = p.id where p.tenant_id = ?",
                tenantId)).isZero();

        // 缺少必填列
        MockHttpServletResponse missing = upload(s, "products", "p.xlsx", xlsx(List.of("商品名称", "单位"),
                List.of(List.of("a", "个"))), "CREATE_ONLY", true);
        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat((String) read(missing, "$.code")).isEqualTo("IMPORT_MISSING_COLUMNS");
    }

    @Test
    void createOnlySkipsAndUpsertUpdatesWithoutTouchingStock() throws Exception {
        Session s = registerWithTenant("ix-up", "更新店");
        Long tenantId = currentTenantId(s, mvc);
        MockHttpServletResponse created = send(post("/api/v1/products"), s, """
                {"name":"旧名","sku":"UP-1","unit":"个","costPrice":"4","retailPrice":"10","lowStockThreshold":2,"initialStock":5}
                """);
        assertThat(created.getStatus()).isEqualTo(201);
        byte[] file = xlsx(PRODUCT_HEADER, List.of(product("新名", 12, 4.5, "UP-1", "新分类", 99),
                product("另一个", 3, 1, "UP-2", null, 7)));

        MockHttpServletResponse skip = upload(s, "products", "p.xlsx", file, "CREATE_ONLY", false);
        assertThat((Integer) read(skip, "$.skipped")).isEqualTo(1);
        assertThat((Integer) read(skip, "$.created")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select name from products where tenant_id = ? and sku = 'UP-1'",
                String.class, tenantId)).isEqualTo("旧名");

        MockHttpServletResponse up = upload(s, "products", "p.xlsx", file, "UPSERT", false);
        assertThat(up.getStatus()).as(up.getContentAsString()).isEqualTo(200);
        assertThat((Integer) read(up, "$.updated")).isEqualTo(2);
        assertThat((List<String>) read(up, "$.notes")).anyMatch(n -> n.contains("初始库存"));
        Map<String, Object> p = jdbcTemplate.queryForMap(
                "select name, retail_price, cost_price, stock_qty, low_stock_threshold, category_id from products where tenant_id = ? and sku = 'UP-1'",
                tenantId);
        assertThat(p.get("name")).isEqualTo("新名");
        assertThat((BigDecimal) p.get("retail_price")).isEqualByComparingTo("12");
        assertThat((BigDecimal) p.get("cost_price")).isEqualByComparingTo("4.5");
        assertThat(p.get("stock_qty")).isEqualTo(5);                // 库存不变
        assertThat(p.get("low_stock_threshold")).isEqualTo(2);      // 没填的列保留原值
        assertThat(p.get("category_id")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select stock_qty from products where tenant_id = ? and sku = 'UP-2'",
                Integer.class, tenantId)).isEqualTo(7);
        assertStockMatchesMovements(tenantId);
    }

    @Test
    void freePlanProductLimitAppliesToImport() throws Exception {
        Session s = registerWithFreeTenant("ix-lim", "上限店");
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            rows.add(product("商品" + i, 1, 1, "L-" + i, null, null));
        }
        MockHttpServletResponse res = upload(s, "products", "p.xlsx", xlsx(PRODUCT_HEADER, rows), "CREATE_ONLY", false);
        assertThat((Boolean) read(res, "$.imported")).isFalse();
        assertThat((Integer) read(res, "$.errors[0].row")).isZero();
        assertThat((String) read(res, "$.errors[0].message")).contains("免费版最多 100 个商品");
        assertThat(count("select count(*) from products where tenant_id = ?", currentTenantId(s, mvc))).isZero();
    }

    // ---------------- 客户、供应商、CSV 编码 ----------------

    @Test
    void customersFromGbkCsvAndSuppliersFromUtf8BomCsv() throws Exception {
        Session s = registerWithTenant("ix-csv", "CSV 店");
        Long tenantId = currentTenantId(s, mvc);
        String customers = "客户名称*,电话,类型,默认价格,地址\r\n张三五金店,13800000001,企业,批发,\"广州市, 天河区\"\r\n李四,13800000002,,,\r\n";
        MockHttpServletResponse c = upload(s, "customers", "客户.csv", customers.getBytes(Charset.forName("GBK")),
                "CREATE_ONLY", false);
        assertThat(c.getStatus()).as(c.getContentAsString()).isEqualTo(200);
        assertThat((Integer) read(c, "$.created")).isEqualTo(2);
        Map<String, Object> zs = jdbcTemplate.queryForMap(
                "select customer_type, price_tier, address from customers where tenant_id = ? and phone = '13800000001'", tenantId);
        assertThat(zs).containsEntry("customer_type", "BUSINESS").containsEntry("price_tier", "WHOLESALE")
                .containsEntry("address", "广州市, 天河区");
        assertThat(jdbcTemplate.queryForObject("select name from customers where tenant_id = ? and phone = '13800000002'",
                String.class, tenantId)).isEqualTo("李四");

        // 再导入：按电话判重，只新增时跳过
        MockHttpServletResponse again = upload(s, "customers", "客户.csv", customers.getBytes(Charset.forName("GBK")),
                "CREATE_ONLY", false);
        assertThat((Integer) read(again, "$.skipped")).isEqualTo(2);

        String suppliers = "﻿供应商名称,联系人,供应商品\n华强配件厂,王经理,数据线\n";
        MockHttpServletResponse sp = upload(s, "suppliers", "供应商.csv", suppliers.getBytes(StandardCharsets.UTF_8),
                "CREATE_ONLY", false);
        assertThat(sp.getStatus()).as(sp.getContentAsString()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select contact_person from suppliers where tenant_id = ? and name = '华强配件厂'",
                String.class, tenantId)).isEqualTo("王经理");
    }

    @Test
    void badFilesAreRejected() throws Exception {
        Session s = registerWithFreeTenant("ix-bad", "坏文件店");
        MockHttpServletResponse garbage = upload(s, "products", "x.xlsx", "PK\u0003\u0004 not really a zip".getBytes(),
                "CREATE_ONLY", true);
        assertThat(garbage.getStatus()).isEqualTo(400);
        assertThat((String) read(garbage, "$.code")).isEqualTo("IMPORT_FILE_UNREADABLE");

        byte[] xls = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0 };
        assertThat((String) read(upload(s, "products", "old.xls", xls, "CREATE_ONLY", true), "$.code"))
                .isEqualTo("IMPORT_FORMAT_UNSUPPORTED");
        assertThat((String) read(upload(s, "products", "a.pdf", "%PDF-1.4".getBytes(), "CREATE_ONLY", true), "$.code"))
                .isEqualTo("IMPORT_FORMAT_UNSUPPORTED");
        assertThat((String) read(upload(s, "products", "e.csv", new byte[0], "CREATE_ONLY", true), "$.code"))
                .isEqualTo("IMPORT_FILE_EMPTY");
        assertThat((String) read(upload(s, "products", "h.csv", "商品名称*,单位*,零售价*,进货成本*\n".getBytes(StandardCharsets.UTF_8),
                "CREATE_ONLY", true), "$.code")).isEqualTo("IMPORT_FILE_EMPTY");
    }

    @Test
    void staffCannotImportOrExport() throws Exception {
        Session owner = registerWithProTenant("ix-own", "权限店");
        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("ix-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session st = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        byte[] file = xlsx(PRODUCT_HEADER, List.of(product("a", 1, 1, "S-1", null, null)));
        assertThat(upload(st, "products", "p.xlsx", file, "CREATE_ONLY", true).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/imports/products/template"), st, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/exports/products"), st, null).getStatus()).isEqualTo(403);
    }

    // ---------------- 导出 ----------------

    @Test
    void excelExportNeedsProAndCsvStaysStandard() throws Exception {
        Session std = registerWithTenant("ix-std", "标准版店");
        MockHttpServletResponse res = send(get("/api/v1/exports/products"), std, null);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat((String) read(res, "$.details.requiredPlan")).isEqualTo("PRO");
        assertThat(send(get("/api/v1/reports/sales/export"), std, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/reports/sales/export").param("format", "xlsx"), std, null).getStatus()).isEqualTo(403);
    }

    @Test
    void exportedWorkbooksMatchDatabase() throws Exception {
        Session s = registerWithProTenant("ix-exp", "导出店");
        Long tenantId = currentTenantId(s, mvc);
        upload(s, "products", "p.xlsx", xlsx(PRODUCT_HEADER, List.of(
                product("导出商品甲", 10, 4, "EX-1", "饮料", 20),
                product("导出商品乙", "2.50", "1.20", "EX-2", null, 100))), "CREATE_ONLY", false);
        Long p1 = jdbcTemplate.queryForObject("select id from products where tenant_id = ? and sku = 'EX-1'", Long.class, tenantId);
        Long p2 = jdbcTemplate.queryForObject("select id from products where tenant_id = ? and sku = 'EX-2'", Long.class, tenantId);
        MockHttpServletResponse sale = send(post("/api/v1/sales-orders"), s, """
                {"priceTier":"RETAIL","items":[{"productId":%d,"quantity":3},{"productId":%d,"quantity":4}]}
                """.formatted(p1, p2));
        assertThat(sale.getStatus()).as(sale.getContentAsString()).isEqualTo(201);

        // 商品：2 行，数字为数字，库存价值 = 成本 × 库存
        try (Workbook wb = book(send(get("/api/v1/exports/products"), s, null))) {
            Sheet sh = wb.getSheetAt(0);
            assertThat(sh.getLastRowNum()).isEqualTo(2);
            Row a = "导出商品甲".equals(sh.getRow(1).getCell(0).getStringCellValue()) ? sh.getRow(1) : sh.getRow(2);
            assertThat(a.getCell(0).getStringCellValue()).isEqualTo("导出商品甲");
            assertThat(a.getCell(3).getStringCellValue()).isEqualTo("饮料");
            assertThat(a.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(a.getCell(8).getNumericCellValue()).isEqualTo(17.0);       // 库存 20 − 3
            assertThat(a.getCell(9).getNumericCellValue()).isEqualTo(68.0);       // 17 × 4
        }

        // 销售：1 单 2 行明细；应收 10×3 + 2.5×4 = 40，成本 4×3 + 1.2×4 = 16.8，毛利 23.2；时间按店铺时区
        try (Workbook wb = book(send(get("/api/v1/exports/sales"), s, null))) {
            Sheet head = wb.getSheet("销售单");
            assertThat(head.getLastRowNum()).isEqualTo(1);
            Row r = head.getRow(1);
            assertThat(r.getCell(7).getNumericCellValue()).isEqualTo(40.0);
            assertThat(r.getCell(8).getNumericCellValue()).isEqualTo(16.8, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(r.getCell(9).getNumericCellValue()).isEqualTo(23.2, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(r.getCell(12).getStringCellValue()).isEqualTo("ix-exp");
            Timestamp orderDate = jdbcTemplate.queryForObject("select order_date from sales_orders where tenant_id = ?",
                    Timestamp.class, tenantId);
            LocalDateTime expected = LocalDateTime.ofInstant(orderDate.toInstant(), ZoneId.of("Asia/Shanghai"));
            Cell date = r.getCell(1);
            assertThat(date.getLocalDateTimeCellValue()).isCloseTo(expected,
                    org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
            assertThat(wb.getSheet("销售明细").getLastRowNum()).isEqualTo(2);
        }

        // 库存流水：2 条初始 + 2 条销售
        try (Workbook wb = book(send(get("/api/v1/exports/movements"), s, null))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(4);
        }
        // 报表 xlsx
        try (Workbook wb = book(send(get("/api/v1/reports/sales/export").param("format", "xlsx"), s, null))) {
            Row total = wb.getSheetAt(0).getRow(wb.getSheetAt(0).getLastRowNum());
            assertThat(total.getCell(2).getNumericCellValue()).isEqualTo(40.0);
        }

        // 店铺隔离：另一家专业版店导出为空
        Session other = registerWithProTenant("ix-oth", "别人的店");
        try (Workbook wb = book(send(get("/api/v1/exports/products"), other, null))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isZero();
        }
        try (Workbook wb = book(send(get("/api/v1/exports/sales"), other, null))) {
            assertThat(wb.getSheet("销售单").getLastRowNum()).isZero();
        }
    }
}
