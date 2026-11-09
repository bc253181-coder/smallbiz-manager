package com.smallbiz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/** 条码：扫码查找、店铺内唯一（删除后可复用）、店内码生成、导入判重、店铺隔离。 */
class BarcodeApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    private MockHttpServletResponse create(Session s, String name, String sku, String barcode) throws Exception {
        return send(post("/api/v1/products"), s, """
                {"name":"%s","sku":%s,"barcode":%s,"unit":"个","costPrice":"5","retailPrice":"10","lowStockThreshold":0,"initialStock":3}
                """.formatted(name, sku == null ? "null" : "\"" + sku + "\"", barcode == null ? "null" : "\"" + barcode + "\""));
    }

    private long id(MockHttpServletResponse res) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private MockHttpServletResponse lookup(Session s, String code) throws Exception {
        return send(get("/api/v1/products/lookup").param("code", code), s, null);
    }

    @Test
    void lookupByBarcodeThenSkuAndHidesCostFromStaff() throws Exception {
        Session owner = registerWithProTenant("bc-lk", "扫码店");
        long id = id(create(owner, "可乐", "COLA-1", "6901234567892"));

        MockHttpServletResponse byBarcode = lookup(owner, " 6901234567892 ");
        assertThat(byBarcode.getStatus()).isEqualTo(200);
        assertThat(((Number) read(byBarcode, "$.id")).longValue()).isEqualTo(id);
        assertThat((String) read(byBarcode, "$.costPrice")).isEqualTo("5.00");
        assertThat(((Number) read(lookup(owner, "COLA-1"), "$.id")).longValue()).isEqualTo(id);

        MockHttpServletResponse missing = lookup(owner, "0000000000000");
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat((String) read(missing, "$.message")).contains("0000000000000");

        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("bc-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        Session st = new Session(staff.email(), staff.password(),
                read(send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null), "$.accessToken"), null, staff.userId());
        MockHttpServletResponse staffView = lookup(st, "6901234567892");
        assertThat(staffView.getStatus()).isEqualTo(200);
        assertThat((Object) read(staffView, "$.costPrice")).isNull();
        // 店员不能生成店内码
        assertThat(send(post("/api/v1/products/barcodes/generate"), st, null).getStatus()).isEqualTo(403);
    }

    @Test
    void barcodeIsUniquePerStoreAndReusableAfterDelete() throws Exception {
        Session s = registerWithFreeTenant("bc-uq", "唯一店");
        long a = id(create(s, "商品甲", "UQ-A", "111222333"));
        long b = id(create(s, "商品乙", "UQ-B", null));

        MockHttpServletResponse dup = create(s, "商品丙", "UQ-C", "111222333");
        assertThat(dup.getStatus()).isEqualTo(409);
        assertThat((String) read(dup, "$.code")).isEqualTo("BARCODE_EXISTS");
        assertThat((String) read(dup, "$.message")).contains("商品甲");

        MockHttpServletResponse upd = send(put("/api/v1/products/" + b), s, """
                {"name":"商品乙","sku":"UQ-B","barcode":"111222333","unit":"个","costPrice":"5","retailPrice":"10","lowStockThreshold":0,"status":"ACTIVE","version":0}
                """);
        assertThat(upd.getStatus()).isEqualTo(409);
        assertThat((String) read(upd, "$.code")).isEqualTo("BARCODE_EXISTS");

        // 修改自己（条码不变）不算重复
        MockHttpServletResponse self = send(put("/api/v1/products/" + a), s, """
                {"name":"商品甲改名","sku":"UQ-A","barcode":"111222333","unit":"个","costPrice":"5","retailPrice":"11","lowStockThreshold":0,"status":"ACTIVE","version":0}
                """);
        assertThat(self.getStatus()).as(self.getContentAsString()).isEqualTo(200);

        // 数据库唯一索引兜底
        Long tenantId = currentTenantId(s, mvc);
        assertThatThrownBy(() -> jdbcTemplate.update("update products set barcode = '111222333' where id = ?", b))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 删除后条码可以给新商品用
        assertThat(send(delete("/api/v1/products/" + a), s, null).getStatus()).isEqualTo(204);
        long c = id(create(s, "商品丁", "UQ-D", "111222333"));
        assertThat(((Number) read(lookup(s, "111222333"), "$.id")).longValue()).isEqualTo(c);
        assertThat(jdbcTemplate.queryForObject("select count(*) from products where tenant_id = ? and barcode = '111222333'",
                Integer.class, tenantId)).isEqualTo(2); // 已删除的商品仍保留原条码
    }

    @Test
    void storesAreIsolated() throws Exception {
        Session a = registerWithFreeTenant("bc-a", "A 店");
        Session b = registerWithFreeTenant("bc-b", "B 店");
        id(create(a, "A 的商品", "ISO-1", "555666777"));
        assertThat(lookup(b, "555666777").getStatus()).isEqualTo(404);
        assertThat(lookup(b, "ISO-1").getStatus()).isEqualTo(404);
        // 不同店铺可以用同一个条码
        long other = id(create(b, "B 的商品", "ISO-1", "555666777"));
        assertThat(((Number) read(lookup(b, "555666777"), "$.id")).longValue()).isEqualTo(other);
    }

    @Test
    void generatedStoreCodesAreValidEan13AndUnique() throws Exception {
        assertThat(ProductService.ean13CheckDigit("400638133393")).isEqualTo(1);   // 4006381333931
        assertThat(ProductService.ean13CheckDigit("690123456789")).isEqualTo(2);   // 6901234567892

        Session s = registerWithFreeTenant("bc-gen", "店内码店");
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = send(post("/api/v1/products/barcodes/generate"), s, null);
            assertThat(res.getStatus()).isEqualTo(200);
            String code = read(res, "$.barcode");
            assertThat(code).hasSize(13).startsWith("20").containsOnlyDigits();
            assertThat(code.charAt(12) - '0').isEqualTo(ProductService.ean13CheckDigit(code.substring(0, 12)));
            codes.add(code);
        }
        assertThat(codes).hasSize(3);

        // 被手工占用的号码会跳过
        String next = "20" + String.format("%010d", 4);
        next = next + ProductService.ean13CheckDigit(next);
        id(create(s, "占用", "GEN-X", next));
        String generated = read(send(post("/api/v1/products/barcodes/generate"), s, null), "$.barcode");
        assertThat(generated).isNotEqualTo(next);
        id(create(s, "用店内码", "GEN-Y", generated));
    }

    @Test
    void importRejectsBarcodeUsedByAnotherProduct() throws Exception {
        Session s = registerWithFreeTenant("bc-imp", "导入条码店");
        id(create(s, "已有商品", "IMP-A", "888999000"));
        String csv = "商品名称*,单位*,零售价*,进货成本*,SKU,条码\n新商品,个,1,1,IMP-B,888999000\n另一个,个,1,1,IMP-C,123123123\n第三个,个,1,1,IMP-D,123123123\n";
        MockHttpServletResponse res = mvc.perform(multipart("/api/v1/imports/products")
                .file(new MockMultipartFile("file", "p.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "true").header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        List<Map<String, Object>> errors = read(res, "$.errors");
        assertThat(errors).extracting(e -> e.get("row") + ":" + e.get("column")).containsExactlyInAnyOrder("2:条码", "4:条码");
        assertThat(errors.get(0).get("message").toString() + errors.get(1).get("message")).contains("已有商品").contains("与第 3 行重复");
    }
}
