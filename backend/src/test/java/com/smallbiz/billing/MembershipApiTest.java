package com.smallbiz.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;

/** 普通会员 + 高级永久会员（docs/16）：次数限制、并发、员工计入店主、后台配置、开通与取消、旧订阅、激活码。 */
class MembershipApiTest extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    TenantMemberRepository members;

    private static final String DEFAULT_CONFIG = """
            {"maxStores":2,"maxProducts":3,"reportDays":5,"advancedTools":false,"advancedTemplates":false,
             "export":false,"advancedAnalytics":false}
            """;

    private static final String PRODUCT = "{\"name\":\"%s\",\"unit\":\"个\",\"costPrice\":\"1\",\"retailPrice\":\"2\",\"lowStockThreshold\":0,\"initialStock\":%d}";

    private Session admin() throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\"}")).andReturn().getResponse();
        if (res.getStatus() != 200) {
            res = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                    .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\",\"displayName\":\"平台\",\"agreeTerms\":true}"))
                    .andReturn().getResponse();
        }
        return new Session("admin@platform.test", "AdminPass123", read(res, "$.accessToken"), refreshCookie(res),
                ((Number) read(res, "$.user.id")).longValue());
    }

    /** 每个测试后恢复方案中的初始配置（其他测试依赖它）。 */
    @AfterEach
    void restoreConfig() throws Exception {
        send(put("/api/v1/admin/membership/config"), admin(), DEFAULT_CONFIG);
    }

    private int create(Session s, String name) throws Exception {
        return send(post("/api/v1/products"), s, PRODUCT.formatted(name, 0)).getStatus();
    }

    @Test
    void concurrentCreatesNeverExceedTheLimit() throws Exception {
        Session s = registerWithFreeTenant("mem-conc", "并发会员店");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                go.await();
                return create(s, "并发" + n);
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            int st = f.get();
            assertThat(st).isIn(201, 403);
            if (st == 201) {
                created++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select products_created from account_usage where user_id = ?", Integer.class,
                s.userId())).isEqualTo(3);
    }

    @Test
    void staffCreatingProductsCountsAgainstTheOwner() throws Exception {
        Session owner = registerWithFreeTenant("mem-own", "员工计数店");
        Long tenantId = currentTenantId(owner, mvc);
        // 后台开放高级工具（员工），让免费店也能有员工
        send(put("/api/v1/admin/membership/config"), admin(), DEFAULT_CONFIG.replace("\"advancedTools\":false", "\"advancedTools\":true"));
        Session staff = register("mem-staff");
        members.save(new TenantMember(tenantId, staff.userId(), MemberRole.ADMIN));
        Session staffInStore = switchTenant(staff, tenantId);
        assertThat(create(staffInStore, "员工建的1")).isEqualTo(201);
        assertThat(create(staffInStore, "员工建的2")).isEqualTo(201);
        assertThat(create(owner, "店主建的")).isEqualTo(201);
        assertThat(create(staffInStore, "员工建的3")).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("select products_created from account_usage where user_id = ?", Integer.class,
                owner.userId())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select coalesce(max(products_created),0) from account_usage where user_id = ?",
                Integer.class, staff.userId())).isZero();
    }

    @Test
    void adminConfigTakesEffectImmediately_andOnlyAdminCanChangeIt() throws Exception {
        Session s = registerWithFreeTenant("mem-cfg", "配置店");
        assertThat(send(get("/api/v1/customers"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(put("/api/v1/admin/membership/config"), s, DEFAULT_CONFIG).getStatus()).isEqualTo(403);

        Session admin = admin();
        MockHttpServletResponse res = send(put("/api/v1/admin/membership/config"), admin, """
                {"maxStores":2,"maxProducts":5,"reportDays":30,"advancedTools":true,"advancedTemplates":false,
                 "export":false,"advancedAnalytics":true}
                """);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/customers"), s, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/reports/sales"), s, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/reports/sales/export"), s, null).getStatus()).isEqualTo(403);
        for (int i = 1; i <= 5; i++) {
            assertThat(create(s, "配置" + i)).isEqualTo(201);
        }
        assertThat(create(s, "配置6")).isEqualTo(403);
        // 报表历史天数：30 天内可以，更早不行
        String d29 = java.time.LocalDate.now().minusDays(29).toString();
        String d40 = java.time.LocalDate.now().minusDays(40).toString();
        assertThat(send(get("/api/v1/reports/sales?from=" + d29), s, null).getStatus()).isEqualTo(200);
        MockHttpServletResponse old = send(get("/api/v1/reports/sales?from=" + d40), s, null);
        assertThat(old.getStatus()).isEqualTo(403);
        assertThat((Integer) read(old, "$.details.reportDays")).isEqualTo(30);

        assertThat(send(put("/api/v1/admin/membership/config"), admin, DEFAULT_CONFIG.replace("\"maxStores\":2", "\"maxStores\":0"))
                .getStatus()).isEqualTo(400);
        assertThat((Integer) read(send(get("/api/v1/admin/membership/config"), admin, null), "$.maxProducts")).isEqualTo(5);
    }

    @Test
    void lifetimeCoversNewStores_revokeKeepsData() throws Exception {
        Session s = registerWithFreeTenant("mem-life", "永久店一");
        Long first = currentTenantId(s, mvc);
        Session admin = admin();
        assertThat(send(post("/api/v1/admin/users/" + s.userId() + "/membership"), admin,
                "{\"membership\":\"LIFETIME\",\"note\":\" \"}").getStatus()).isEqualTo(400); // 必须填原因
        assertThat(send(post("/api/v1/admin/users/" + s.userId() + "/membership"), admin,
                "{\"membership\":\"LIFETIME\",\"note\":\"补偿\"}").getStatus()).isEqualTo(200);
        assertThat(send(post("/api/v1/admin/users/" + s.userId() + "/membership"), admin,
                "{\"membership\":\"LIFETIME\",\"note\":\"重复\"}").getStatus()).isEqualTo(409);

        // 新开的店铺自动是高级会员；可以开超过 2 家
        Session s2 = createTenant(s, "永久店二");
        Session s3 = createTenant(s2, "永久店三");
        assertThat((Boolean) read(send(get("/api/v1/billing/current"), s3, null), "$.lifetime")).isTrue();
        for (int i = 0; i < 5; i++) {
            assertThat(create(s3, "永久商品" + i)).isEqualTo(201);
        }
        assertThat(send(post("/api/v1/customers"), s3, "{\"name\":\"老客户\",\"customerType\":\"INDIVIDUAL\",\"priceTier\":\"RETAIL\"}")
                .getStatus()).isEqualTo(201);

        // 取消（例如退款）：回到普通会员，数据都在，已有商品可以查看
        assertThat(send(post("/api/v1/admin/users/" + s.userId() + "/membership"), admin,
                "{\"membership\":\"FREE\",\"note\":\"退款\"}").getStatus()).isEqualTo(200);
        MockHttpServletResponse cur = send(get("/api/v1/billing/current"), s3, null);
        assertThat((String) read(cur, "$.plan")).isEqualTo("FREE");
        assertThat(send(get("/api/v1/products?size=50"), s3, null).getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from customers where name = '老客户' and tenant_id = ?",
                Integer.class, currentTenantId(s3, mvc))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select plan from tenants where id = ?", String.class, first)).isEqualTo("FREE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from membership_logs where user_id = ?", Integer.class, s.userId()))
                .isEqualTo(2);
    }

    @Test
    void legacySubscriptionKeepsWorking_thenFallsBackToFreeLimits() throws Exception {
        Session s = registerWithTenant("mem-legacy", "老订阅店"); // 标准版，一年
        Long tenantId = currentTenantId(s, mvc);
        for (int i = 0; i < 5; i++) {
            assertThat(create(s, "老订阅" + i)).isEqualTo(201); // 有效期内不计数
        }
        assertThat(send(get("/api/v1/customers"), s, null).getStatus()).isEqualTo(200);
        jdbcTemplate.update("update tenants set plan_expires_at = date_sub(utc_timestamp(6), interval 1 day) where id = ?", tenantId);
        // 到期：已有商品照常查看，按普通会员计数
        assertThat((Integer) read(send(get("/api/v1/products"), s, null), "$.totalItems")).isEqualTo(5);
        for (int i = 0; i < 3; i++) {
            assertThat(create(s, "到期后" + i)).isEqualTo(201);
        }
        assertThat(create(s, "到期后超出")).isEqualTo(403);
    }

    @Test
    void lifetimeActivationCodeGrantsAllOwnedStores() throws Exception {
        Session admin = admin();
        MockHttpServletResponse batch = send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"永久码\",\"plan\":\"PRO\",\"durationDays\":0,\"trial\":false,\"lifetime\":true,\"quantity\":2}");
        assertThat(batch.getStatus()).as(batch.getContentAsString()).isEqualTo(201);
        List<String> codes = read(batch, "$.codes");

        Session s = registerWithTenant("mem-code", "兑换店一"); // 一家有旧订阅
        Session s2 = createTenant(s, "兑换店二");
        MockHttpServletResponse ok = send(post("/api/v1/billing/redeem"), s2, "{\"code\":\"%s\"}".formatted(codes.get(0)));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat((Boolean) read(ok, "$.lifetime")).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from tenants t join tenant_members m on m.tenant_id = t.id "
                + "where m.user_id = ? and m.role = 'OWNER' and t.plan = 'PRO' and t.plan_expires_at is null", Integer.class,
                s.userId())).isEqualTo(2);
        // 再兑换一个：已经是永久会员
        assertThat((String) read(send(post("/api/v1/billing/redeem"), s2, "{\"code\":\"%s\"}".formatted(codes.get(1))), "$.code"))
                .isEqualTo("ALREADY_LIFETIME");
        // 旧的按天数激活码也不能把永久会员改成有到期时间
        String old = read(send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"旧码\",\"plan\":\"STANDARD\",\"durationDays\":30,\"trial\":false,\"quantity\":1}"), "$.codes[0]");
        assertThat((String) read(send(post("/api/v1/billing/redeem"), s2, "{\"code\":\"%s\"}".formatted(old)), "$.code"))
                .isEqualTo("ALREADY_LIFETIME");
        assertThat((Object) read(send(get("/api/v1/billing/current"), s2, null), "$.expiresAt")).isNull();
    }

    private Session switchTenant(Session s, Long tenantId) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/tenants/" + tenantId + "/switch"), s, null);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return new Session(s.email(), s.password(), read(res, "$.accessToken"), refreshCookie(res), s.userId());
    }
}
