package com.smallbiz.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/** 客户与供应商接口测试。 */
class PartnerApiTest extends IntegrationTest {

    @Autowired
    TenantMemberRepository members;

    private static String customer(String name, String phone, String type, String tier) {
        return """
                {"name":"%s","phone":"%s","email":"","address":"吉隆坡","customerType":"%s","priceTier":"%s","notes":"老客户"}
                """.formatted(name, phone, type, tier);
    }

    private Long createCustomer(Session s, String name, String phone, String type, String tier) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/customers"), s, customer(name, phone, type, tier));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Long createSupplier(Session s, String name) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/suppliers"), s, """
                {"name":"%s","contactPerson":"陈先生","phone":"+86 138 0000 0000","email":"chen@supplier.test",
                 "suppliedProductsNote":"手机壳、数据线"}
                """.formatted(name));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Session staffOf(Session owner) throws Exception {
        Long tenantId = currentTenantId(owner, mvc);
        Session staff = register("ptn-staff");
        allowStaff(tenantId);
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        return new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());
    }

    // ---------------- 客户 ----------------

    @Test
    void customerCrudAndSearch() throws Exception {
        Session s = registerWithTenant("cus-crud", "客户店");
        Long id = createCustomer(s, "王老板", "13800001111", "BUSINESS", "WHOLESALE");
        createCustomer(s, "李小姐", "0123456789", "INDIVIDUAL", "RETAIL");
        createCustomer(s, "张三 100%", "", "INDIVIDUAL", "RETAIL");

        MockHttpServletResponse got = send(get("/api/v1/customers/" + id), s, null);
        assertThat((String) read(got, "$.priceTier")).isEqualTo("WHOLESALE");
        assertThat((Object) read(got, "$.email")).isNull(); // 空字符串存为 null

        assertThat((Integer) read(send(get("/api/v1/customers?q=老板"), s, null), "$.totalItems")).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/customers?q=1111"), s, null), "$.totalItems")).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/customers").param("q", "%"), s, null), "$.totalItems"))
                .isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/customers?priceTier=WHOLESALE"), s, null), "$.totalItems"))
                .isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/customers?type=INDIVIDUAL"), s, null), "$.totalItems"))
                .isEqualTo(2);
        assertThat((List<String>) read(send(get("/api/v1/customers?sort=name,asc"), s, null), "$.items[*].name"))
                .hasSize(3);

        String update = """
                {"name":"王总","phone":"13800001111","customerType":"BUSINESS","priceTier":"RETAIL","version":%d}
                """;
        MockHttpServletResponse ok = send(put("/api/v1/customers/" + id), s, update.formatted(0));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat((String) read(ok, "$.name")).isEqualTo("王总");
        assertThat((Object) read(ok, "$.notes")).isNull();

        MockHttpServletResponse stale = send(put("/api/v1/customers/" + id), s, update.formatted(0));
        assertThat((String) read(stale, "$.code")).isEqualTo("CONCURRENT_MODIFICATION");

        assertThat(send(delete("/api/v1/customers/" + id), s, null).getStatus()).isEqualTo(204);
        assertThat(send(get("/api/v1/customers/" + id), s, null).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/customers"), s, null), "$.totalItems")).isEqualTo(2);
    }

    @Test
    void customerValidation() throws Exception {
        Session s = registerWithTenant("cus-val", "校验店");
        MockHttpServletResponse res = send(post("/api/v1/customers"), s, """
                {"name":" ","phone":"abc","email":"not-email","customerType":null,"priceTier":"VIP"}
                """);
        assertThat(res.getStatus()).isEqualTo(400);
        MockHttpServletResponse res2 = send(post("/api/v1/customers"), s, """
                {"name":" ","phone":"abc","email":"not-email","customerType":null,"priceTier":"RETAIL"}
                """);
        assertThat(res2.getStatus()).isEqualTo(400);
        assertThat((String) read(res2, "$.details.fields.name")).isNotBlank();
        assertThat((String) read(res2, "$.details.fields.phone")).isNotBlank();
        assertThat((String) read(res2, "$.details.fields.email")).isNotBlank();
        assertThat((String) read(res2, "$.details.fields.customerType")).isNotBlank();
    }

    @Test
    void staffCanViewAndAddCustomers_butNotEditOrDelete() throws Exception {
        Session owner = registerWithTenant("cus-own", "权限店");
        Long id = createCustomer(owner, "老客户", "1", "INDIVIDUAL", "RETAIL");
        Session staff = staffOf(owner);

        assertThat(send(get("/api/v1/customers/" + id), staff, null).getStatus()).isEqualTo(200);
        createCustomer(staff, "店员登记的新客户", "2", "INDIVIDUAL", "RETAIL");
        assertThat(send(put("/api/v1/customers/" + id), staff,
                "{\"name\":\"改\",\"customerType\":\"INDIVIDUAL\",\"priceTier\":\"RETAIL\",\"version\":0}").getStatus())
                .isEqualTo(403);
        assertThat(send(delete("/api/v1/customers/" + id), staff, null).getStatus()).isEqualTo(403);
    }

    @Test
    void customersAreIsolatedBetweenTenants() throws Exception {
        Session a = registerWithTenant("cus-iso-a", "客户A");
        Session b = registerWithTenant("cus-iso-b", "客户B");
        Long idA = createCustomer(a, "A 的大客户", "999", "BUSINESS", "WHOLESALE");

        assertThat(send(get("/api/v1/customers/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/customers/" + idA), b,
                "{\"name\":\"改\",\"customerType\":\"INDIVIDUAL\",\"priceTier\":\"RETAIL\",\"version\":0}").getStatus())
                .isEqualTo(404);
        assertThat(send(delete("/api/v1/customers/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/customers?q=大客户"), b, null), "$.totalItems")).isZero();
        assertThat((String) read(send(get("/api/v1/customers/" + idA), a, null), "$.name")).isEqualTo("A 的大客户");
    }

    // ---------------- 供应商 ----------------

    @Test
    void supplierCrudAndSearch() throws Exception {
        Session s = registerWithTenant("sup-crud", "供应商店");
        Long id = createSupplier(s, "深圳华强配件厂");
        createSupplier(s, "义乌小商品批发");

        assertThat((Integer) read(send(get("/api/v1/suppliers?q=华强"), s, null), "$.totalItems")).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/suppliers?q=陈"), s, null), "$.totalItems")).isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/suppliers?q=数据线"), s, null), "$.totalItems")).isEqualTo(2);

        MockHttpServletResponse ok = send(put("/api/v1/suppliers/" + id), s,
                "{\"name\":\"深圳华强电子\",\"contactPerson\":\"\",\"version\":0}");
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat((Object) read(ok, "$.contactPerson")).isNull();

        assertThat(send(delete("/api/v1/suppliers/" + id), s, null).getStatus()).isEqualTo(204);
        assertThat(send(get("/api/v1/suppliers/" + id), s, null).getStatus()).isEqualTo(404);
    }

    @Test
    void staffCannotAccessSuppliers() throws Exception {
        Session owner = registerWithTenant("sup-own", "供应商权限店");
        Long id = createSupplier(owner, "供应商");
        Session staff = staffOf(owner);
        assertThat(send(get("/api/v1/suppliers"), staff, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/suppliers/" + id), staff, null).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/v1/suppliers"), staff, "{\"name\":\"x\"}").getStatus()).isEqualTo(403);
    }

    @Test
    void suppliersAreIsolatedBetweenTenants() throws Exception {
        Session a = registerWithTenant("sup-iso-a", "供应A");
        Session b = registerWithTenant("sup-iso-b", "供应B");
        Long idA = createSupplier(a, "A 的独家货源");

        assertThat(send(get("/api/v1/suppliers/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/suppliers/" + idA), b, "{\"name\":\"改\",\"version\":0}").getStatus())
                .isEqualTo(404);
        assertThat(send(delete("/api/v1/suppliers/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/suppliers"), b, null), "$.totalItems")).isZero();
        assertThat((String) read(send(get("/api/v1/suppliers/" + idA), a, null), "$.name")).isEqualTo("A 的独家货源");
    }
}
