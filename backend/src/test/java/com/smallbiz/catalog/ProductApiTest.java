package com.smallbiz.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.smallbiz.inventory.StockMovementRepository;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantContext;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

class ProductApiTest extends IntegrationTest {

    @Autowired
    StockMovementRepository movementRepo;

    @Autowired
    ProductRepository productRepo;

    @Autowired
    TenantMemberRepository members;

    @Autowired
    TransactionTemplate tx;

    private static String product(String name, String sku, Long categoryId, int initialStock) {
        return """
                {"name":"%s","sku":%s,"categoryId":%s,"unit":"个","costPrice":"8.50","retailPrice":"19.90",
                 "wholesalePrice":"12.00","lowStockThreshold":5,"initialStock":%d,"description":"测试商品"}
                """.formatted(name, sku == null ? "null" : "\"" + sku + "\"", categoryId, initialStock);
    }

    private Long createProduct(Session s, String name, String sku, Long categoryId, int initialStock) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, product(name, sku, categoryId, initialStock));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private Long createCategory(Session s, String name, Long parentId) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/categories"), s,
                "{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parentId));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    // ---------------- 新增 ----------------

    @Test
    void createWithInitialStock_writesMovementAndMoneyAsStrings() throws Exception {
        Session s = registerWithTenant("p-create", "商品测试店");
        Long id = createProduct(s, "数据线 Type-C", "CABLE-01", null, 30);

        MockHttpServletResponse res = send(get("/api/v1/products/" + id), s, null);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((String) read(res, "$.retailPrice")).isEqualTo("19.90");
        assertThat((String) read(res, "$.costPrice")).isEqualTo("8.50");
        assertThat((Integer) read(res, "$.stockQty")).isEqualTo(30);
        assertThat((Boolean) read(res, "$.lowStock")).isFalse();

        MockHttpServletResponse mv = send(get("/api/v1/products/" + id + "/movements"), s, null);
        assertThat((Integer) read(mv, "$.totalItems")).isEqualTo(1);
        assertThat((String) read(mv, "$.items[0].type")).isEqualTo("INITIAL");
        assertThat((Integer) read(mv, "$.items[0].qtyAfter")).isEqualTo(30);

        assertStockMatchesMovements(s, id);
    }

    @Test
    void createWithoutInitialStock_hasNoMovement() throws Exception {
        Session s = registerWithTenant("p-zero", "零库存店");
        Long id = createProduct(s, "空库存商品", null, null, 0);
        MockHttpServletResponse mv = send(get("/api/v1/products/" + id + "/movements"), s, null);
        assertThat((Integer) read(mv, "$.totalItems")).isZero();
        MockHttpServletResponse res = send(get("/api/v1/products/" + id), s, null);
        assertThat((Boolean) read(res, "$.lowStock")).isTrue(); // 0 <= 预警值 5
    }

    @Test
    void autoSkuIsSequentialPerTenant() throws Exception {
        Session a = registerWithTenant("p-auto-a", "自动编号A");
        Session b = registerWithTenant("p-auto-b", "自动编号B");
        Long a1 = createProduct(a, "A1", null, null, 0);
        Long a2 = createProduct(a, "A2", null, null, 0);
        Long b1 = createProduct(b, "B1", null, null, 0);
        assertThat((String) read(send(get("/api/v1/products/" + a1), a, null), "$.sku")).isEqualTo("SKU000001");
        assertThat((String) read(send(get("/api/v1/products/" + a2), a, null), "$.sku")).isEqualTo("SKU000002");
        assertThat((String) read(send(get("/api/v1/products/" + b1), b, null), "$.sku")).isEqualTo("SKU000001");
    }

    @Test
    void skuUniqueWithinTenantButAllowedAcrossTenants() throws Exception {
        Session a = registerWithTenant("p-sku-a", "SKU店A");
        Session b = registerWithTenant("p-sku-b", "SKU店B");
        createProduct(a, "商品", "SAME-SKU", null, 0);

        MockHttpServletResponse dup = send(post("/api/v1/products"), a, product("另一个", "SAME-SKU", null, 0));
        assertThat(dup.getStatus()).isEqualTo(409);
        assertThat((String) read(dup, "$.code")).isEqualTo("SKU_EXISTS");

        createProduct(b, "B 店同 SKU", "SAME-SKU", null, 0); // 其他店铺可以用
    }

    @Test
    void validationErrorsAreReturnedPerField() throws Exception {
        Session s = registerWithTenant("p-valid", "校验店");
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"","sku":"bad sku!","unit":"个","costPrice":"-1","retailPrice":"1.234",
                 "lowStockThreshold":-1,"initialStock":-5}
                """);
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat((String) read(res, "$.details.fields.name")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.sku")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.costPrice")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.retailPrice")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.lowStockThreshold")).isNotBlank();
        assertThat((String) read(res, "$.details.fields.initialStock")).isNotBlank();
    }

    // ---------------- 修改 / 删除 ----------------

    @Test
    void updateDoesNotChangeStock_andDetectsStaleVersion() throws Exception {
        Session s = registerWithTenant("p-upd", "修改店");
        Long id = createProduct(s, "原名", "UPD-1", null, 10);
        String body = """
                {"name":"新名","sku":"UPD-1","unit":"箱","costPrice":"9","retailPrice":"20","lowStockThreshold":2,
                 "status":"INACTIVE","version":%d,"stockQty":99999}
                """;
        MockHttpServletResponse ok = send(put("/api/v1/products/" + id), s, body.formatted(0));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat((String) read(ok, "$.name")).isEqualTo("新名");
        assertThat((Integer) read(ok, "$.stockQty")).isEqualTo(10); // 库存不能通过修改商品改变
        assertThat((String) read(ok, "$.status")).isEqualTo("INACTIVE");

        MockHttpServletResponse stale = send(put("/api/v1/products/" + id), s, body.formatted(0));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat((String) read(stale, "$.code")).isEqualTo("CONCURRENT_MODIFICATION");
        assertStockMatchesMovements(s, id);
    }

    @Test
    void softDeleteHidesProductAndFreesSku() throws Exception {
        Session s = registerWithTenant("p-del", "删除店");
        Long id = createProduct(s, "要删除的", "DEL-1", null, 3);
        assertThat(send(delete("/api/v1/products/" + id), s, null).getStatus()).isEqualTo(204);
        assertThat(send(get("/api/v1/products/" + id), s, null).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/products?q=要删除"), s, null), "$.totalItems")).isZero();
        createProduct(s, "新商品用旧 SKU", "DEL-1", null, 0);
    }

    // ---------------- 列表：搜索 / 筛选 / 排序 / 分页 ----------------

    @Test
    void listSupportsSearchFilterSortAndPaging() throws Exception {
        Session s = registerWithTenant("p-list", "列表店");
        Long phone = createCategory(s, "手机配件", null);
        Long cable = createCategory(s, "数据线", phone);
        Long beauty = createCategory(s, "美妆", null);
        createProduct(s, "苹果数据线", "C-1", cable, 100);
        createProduct(s, "安卓充电头", "C-2", phone, 2);   // 低库存（2 <= 5）
        createProduct(s, "口红 50%off", "B-1", beauty, 50);

        assertThat((Integer) read(send(get("/api/v1/products?q=数据"), s, null), "$.totalItems")).isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/products?q=c-"), s, null), "$.totalItems")).isEqualTo(2);
        // 按一级分类筛选时包含子分类的商品
        assertThat((Integer) read(send(get("/api/v1/products?categoryId=" + phone), s, null), "$.totalItems"))
                .isEqualTo(2);
        assertThat((Integer) read(send(get("/api/v1/products?lowStock=true"), s, null), "$.totalItems")).isEqualTo(1);
        // LIKE 通配符被转义：搜索 "%" 只匹配名字里真的有 % 的商品
        assertThat((Integer) read(send(get("/api/v1/products").param("q", "%"), s, null), "$.totalItems"))
                .isEqualTo(1);
        assertThat((Integer) read(send(get("/api/v1/products").param("q", "_"), s, null), "$.totalItems"))
                .isZero();

        MockHttpServletResponse sorted = send(get("/api/v1/products?sort=stockQty,asc&size=2"), s, null);
        assertThat((List<String>) read(sorted, "$.items[*].name")).containsExactly("安卓充电头", "口红 50%off");
        assertThat((Integer) read(sorted, "$.totalPages")).isEqualTo(2);

        MockHttpServletResponse withCat = send(get("/api/v1/products?q=苹果"), s, null);
        assertThat((String) read(withCat, "$.items[0].categoryName")).isEqualTo("手机配件 / 数据线");

        assertThat(send(get("/api/v1/products?sort=passwordHash,asc"), s, null).getStatus()).isEqualTo(400);
    }

    // ---------------- 分类 ----------------

    @Test
    void categoryRules() throws Exception {
        Session s = registerWithTenant("p-cat", "分类店");
        Long parent = createCategory(s, "服装", null);
        Long child = createCategory(s, "上衣", parent);

        MockHttpServletResponse tooDeep = send(post("/api/v1/categories"), s,
                "{\"name\":\"T恤\",\"parentId\":" + child + "}");
        assertThat((String) read(tooDeep, "$.code")).isEqualTo("CATEGORY_TOO_DEEP");

        MockHttpServletResponse dupRoot = send(post("/api/v1/categories"), s, "{\"name\":\"服装\"}");
        assertThat((String) read(dupRoot, "$.code")).isEqualTo("CATEGORY_NAME_EXISTS");

        assertThat((String) read(send(delete("/api/v1/categories/" + parent), s, null), "$.code"))
                .isEqualTo("CATEGORY_HAS_CHILDREN");

        Long pid = createProduct(s, "卫衣", null, child, 0);
        assertThat((String) read(send(delete("/api/v1/categories/" + child), s, null), "$.code"))
                .isEqualTo("CATEGORY_NOT_EMPTY");

        MockHttpServletResponse tree = send(get("/api/v1/categories"), s, null);
        assertThat((String) read(tree, "$[0].children[0].name")).isEqualTo("上衣");
        assertThat((Integer) read(tree, "$[0].children[0].productCount")).isEqualTo(1);

        // 改名为自己的原名不报重名
        assertThat(send(put("/api/v1/categories/" + child), s, "{\"name\":\"上衣\"}").getStatus()).isEqualTo(204);

        send(delete("/api/v1/products/" + pid), s, null);
        assertThat(send(delete("/api/v1/categories/" + child), s, null).getStatus()).isEqualTo(204);
    }

    // ---------------- 权限 ----------------

    @Test
    void staffCanViewWithoutCost_butCannotModify() throws Exception {
        Session owner = registerWithTenant("p-owner", "权限店");
        Long tenantId = currentTenantId(owner, mvc);
        Long id = createProduct(owner, "商品", "PERM-1", null, 5);

        Session staff = register("p-staff");
        allowStaff(tenantId);
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.STAFF));
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), staff, null);
        Session staffIn = new Session(staff.email(), staff.password(), read(sw, "$.accessToken"), null, staff.userId());

        MockHttpServletResponse view = send(get("/api/v1/products/" + id), staffIn, null);
        assertThat(view.getStatus()).isEqualTo(200);
        assertThat((Object) read(view, "$.costPrice")).isNull();
        assertThat((Object) read(send(get("/api/v1/products/" + id + "/movements"), staffIn, null),
                "$.items[0].unitCost")).isNull();

        assertThat(send(post("/api/v1/products"), staffIn, product("x", null, null, 0)).getStatus()).isEqualTo(403);
        assertThat(send(delete("/api/v1/products/" + id), staffIn, null).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/v1/categories"), staffIn, "{\"name\":\"x\"}").getStatus()).isEqualTo(403);
    }

    // ---------------- 跨店铺隔离 ----------------

    @Test
    void anotherTenantCannotSeeOrTouchProducts() throws Exception {
        Session a = registerWithTenant("p-iso-a", "隔离A");
        Session b = registerWithTenant("p-iso-b", "隔离B");
        Long catA = createCategory(a, "A的分类", null);
        Long idA = createProduct(a, "A 的秘密商品", "SECRET", catA, 7);
        String update = """
                {"name":"被篡改","sku":"SECRET","unit":"个","costPrice":"0","retailPrice":"0","lowStockThreshold":0,
                 "status":"ACTIVE","version":0}
                """;

        assertThat(send(get("/api/v1/products/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/products/" + idA + "/movements"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/products/" + idA), b, update).getStatus()).isEqualTo(404);
        assertThat(send(delete("/api/v1/products/" + idA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(delete("/api/v1/products/" + idA + "/image"), b, null).getStatus()).isEqualTo(404);
        assertThat((Integer) read(send(get("/api/v1/products?q=秘密"), b, null), "$.totalItems")).isZero();
        assertThat((Integer) read(send(get("/api/v1/products"), b, null), "$.totalItems")).isZero();

        // B 不能使用 A 的分类，也不能修改或删除 A 的分类
        assertThat(send(post("/api/v1/products"), b, product("x", null, catA, 0)).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/products?categoryId=" + catA), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/categories/" + catA), b, "{\"name\":\"改\"}").getStatus()).isEqualTo(404);
        assertThat(send(delete("/api/v1/categories/" + catA), b, null).getStatus()).isEqualTo(404);
        assertThat((List<?>) read(send(get("/api/v1/categories"), b, null), "$")).isEmpty();

        // A 的数据完好
        MockHttpServletResponse still = send(get("/api/v1/products/" + idA), a, null);
        assertThat((String) read(still, "$.name")).isEqualTo("A 的秘密商品");
        assertThat((Integer) read(still, "$.stockQty")).isEqualTo(7);
    }

    // ---------------- 图片 ----------------

    @Test
    void imageUploadValidatesRealContent() throws Exception {
        Session a = registerWithTenant("p-img", "图片店");
        Session b = registerWithTenant("p-img-b", "图片店B");
        Long id = createProduct(a, "有图商品", null, null, 0);

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};
        MockHttpServletResponse ok = upload(id, a, new MockMultipartFile("file", "a.png", "image/png", png));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        String url = JsonPath.read(ok.getContentAsString(), "$.imageUrl");
        assertThat(url).startsWith("/uploads/t").endsWith(".png");

        // 图片可公开读取（随机文件名）
        assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).isEqualTo(200);

        // 伪装成图片的文本文件被拒绝
        MockHttpServletResponse fake = upload(id, a,
                new MockMultipartFile("file", "evil.png", "image/png", "<script>".getBytes()));
        assertThat(fake.getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(fake.getContentAsString(), "$.code")).isEqualTo("IMAGE_TYPE_NOT_ALLOWED");

        // 其他店铺不能给它传图
        MockHttpServletResponse cross = upload(id, b, new MockMultipartFile("file", "a.png", "image/png", png));
        assertThat(cross.getStatus()).isEqualTo(404);

        MockHttpServletResponse removed = send(delete("/api/v1/products/" + id + "/image"), a, null);
        assertThat((Object) read(removed, "$.imageUrl")).isNull();
        assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse upload(Long id, Session s, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/v1/products/" + id + "/image").file(file)
                .header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
    }

    // ---------------- 一致性 ----------------

    private void assertStockMatchesMovements(Session s, Long productId) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> {
            Product p = productRepo.findById(productId).orElseThrow();
            assertThat((long) p.getStockQty()).isEqualTo(movementRepo.sumChangeByProductId(productId));
        }));
    }
}
