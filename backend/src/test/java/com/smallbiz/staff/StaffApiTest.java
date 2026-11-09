package com.smallbiz.staff;

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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * 员工邀请与成员管理：邀请链接、名额、立即生效的移除/改角色、专业版到期、店铺隔离、开单人筛选。
 */
class StaffApiTest extends IntegrationTest {

    // ---------------- 辅助 ----------------

    private String invite(Session owner, String role, String note) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/invitations"), owner,
                "{\"role\":\"%s\",\"note\":\"%s\"}".formatted(role, note));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return read(res, "$.token");
    }

    private MockHttpServletResponse accept(Session s, String token) throws Exception {
        MockHttpServletResponse res = mvc.perform(withAuth(post("/api/v1/invitations/accept"), s)
                .contentType("application/json").content("{\"token\":\"%s\"}".formatted(token))
                .cookie(new Cookie("sb_refresh", s.refreshToken() == null ? "" : s.refreshToken())))
                .andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    /** 注册新用户并通过邀请加入，返回在该店铺内的会话（含刷新令牌）。 */
    private Session join(Session owner, String prefix, String role) throws Exception {
        String token = invite(owner, role, prefix);
        Session u = register(prefix);
        MockHttpServletResponse res = accept(u, token);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        assertThat((String) read(res, "$.currentTenant.role")).isEqualTo(role);
        return new Session(u.email(), u.password(), read(res, "$.accessToken"), refreshCookie(res), u.userId());
    }

    private MockHttpServletResponse refresh(Session s) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("sb_refresh", s.refreshToken())))
                .andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private long memberId(Session owner, Long userId) throws Exception {
        List<Number> ids = read(send(get("/api/v1/members"), owner, null), "$.members[?(@.userId == " + userId + ")].id");
        return ids.get(0).longValue();
    }

    private MockHttpServletResponse preview(String token) throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/v1/invitations/preview").param("token", token))
                .andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private long product(Session s) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"员工测试品 %s","unit":"个","costPrice":"5","retailPrice":"10","lowStockThreshold":0,"initialStock":100}
                """.formatted(System.nanoTime()));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    // ---------------- 测试 ----------------

    @Test
    void inviteAndAcceptAsStaff() throws Exception {
        Session owner = registerWithProTenant("st-own", "员工测试店");
        String token = invite(owner, "STAFF", "小王");

        // 未登录可预览
        MockHttpServletResponse pv = preview(token);
        assertThat(pv.getStatus()).isEqualTo(200);
        assertThat((String) read(pv, "$.tenantName")).isEqualTo("员工测试店");
        assertThat((String) read(pv, "$.role")).isEqualTo("STAFF");
        assertThat((String) read(pv, "$.inviterName")).isEqualTo("st-own");

        Session newbie = register("st-new");
        MockHttpServletResponse res = accept(newbie, token);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        Session staff = new Session(newbie.email(), newbie.password(), read(res, "$.accessToken"), refreshCookie(res),
                newbie.userId());
        assertThat((String) read(res, "$.currentTenant.name")).isEqualTo("员工测试店");

        // 店员权限：能看商品和开单，不能看供应商、不能管理员工
        assertThat(send(get("/api/v1/products"), staff, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/suppliers"), staff, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/members"), staff, null).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/v1/invitations"), staff, "{\"role\":\"STAFF\"}").getStatus()).isEqualTo(403);

        // 链接只能用一次
        MockHttpServletResponse again = accept(register("st-late"), token);
        assertThat(again.getStatus()).isEqualTo(404);
        assertThat((String) read(again, "$.code")).isEqualTo("INVITATION_INVALID");
        assertThat(preview(token).getStatus()).isEqualTo(404);

        // 店主看到 2 个成员，名额 1 / 5
        MockHttpServletResponse list = send(get("/api/v1/members"), owner, null);
        assertThat((List<?>) read(list, "$.members")).hasSize(2);
        assertThat((String) read(list, "$.members[0].role")).isEqualTo("OWNER");
        assertThat((Integer) read(list, "$.used")).isEqualTo(1);
        assertThat((Integer) read(list, "$.max")).isEqualTo(5);
        assertThat((List<?>) read(list, "$.invitations")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select status from invitations where token_hash = sha2(?, 256)",
                String.class, token)).isEqualTo("ACCEPTED");
    }

    @Test
    void invalidInvitations() throws Exception {
        Session owner = registerWithProTenant("st-inv", "邀请无效店");

        // 撤销
        String revoked = invite(owner, "STAFF", "a");
        Number id = read(send(get("/api/v1/members"), owner, null), "$.invitations[0].id");
        assertThat(send(post("/api/v1/invitations/" + id + "/revoke"), owner, null).getStatus()).isEqualTo(204);
        assertThat(send(post("/api/v1/invitations/" + id + "/revoke"), owner, null).getStatus()).isEqualTo(409);
        assertThat(preview(revoked).getStatus()).isEqualTo(404);
        assertThat(accept(register("st-r"), revoked).getStatus()).isEqualTo(404);

        // 过期
        String expired = invite(owner, "STAFF", "b");
        jdbcTemplate.update("update invitations set expires_at = date_sub(utc_timestamp(6), interval 1 minute) "
                + "where token_hash = sha2(?, 256)", expired);
        assertThat(preview(expired).getStatus()).isEqualTo(404);
        assertThat(accept(register("st-e"), expired).getStatus()).isEqualTo(404);

        // 不存在
        assertThat(preview("not-a-real-token").getStatus()).isEqualTo(404);

        // 已是成员（店主自己点了链接）：不消耗邀请
        String own = invite(owner, "ADMIN", "c");
        MockHttpServletResponse self = accept(owner, own);
        assertThat(self.getStatus()).isEqualTo(409);
        assertThat((String) read(self, "$.code")).isEqualTo("ALREADY_MEMBER");
        assertThat(preview(own).getStatus()).isEqualTo(200);

        // 不能邀请为店主
        assertThat(send(post("/api/v1/invitations"), owner, "{\"role\":\"OWNER\"}").getStatus()).isEqualTo(400);
    }

    @Test
    void memberLimitCountsPendingInvitations() throws Exception {
        Session owner = registerWithProTenant("st-lim", "名额店");
        join(owner, "st-l1", "STAFF");
        join(owner, "st-l2", "ADMIN");
        for (int i = 0; i < 3; i++) {
            invite(owner, "STAFF", "p" + i);
        }
        // 2 人在职 + 3 个待接受 = 5，第 6 个被拒
        MockHttpServletResponse sixth = send(post("/api/v1/invitations"), owner, "{\"role\":\"STAFF\"}");
        assertThat(sixth.getStatus()).isEqualTo(409);
        assertThat((String) read(sixth, "$.code")).isEqualTo("MEMBER_LIMIT_REACHED");
        assertThat((Integer) read(send(get("/api/v1/members"), owner, null), "$.used")).isEqualTo(5);

        // 撤销一个就能再邀请
        Number id = read(send(get("/api/v1/members"), owner, null), "$.invitations[0].id");
        send(post("/api/v1/invitations/" + id + "/revoke"), owner, null);
        invite(owner, "STAFF", "again");
    }

    @Test
    void concurrentAcceptOfSameLinkSucceedsOnce() throws Exception {
        Session owner = registerWithProTenant("st-cc", "并发店");
        String token = invite(owner, "STAFF", "cc");
        List<Session> users = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            users.add(register("st-cc" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Session u : users) {
            results.add(pool.submit(() -> {
                start.await();
                return accept(u, token).getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Integer> f : results) {
            if (f.get(30, TimeUnit.SECONDS) == 200) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        Long tenantId = currentTenantId(owner, mvc);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tenant_members where tenant_id = ? and status = 'ACTIVE' and role <> 'OWNER'",
                Integer.class, tenantId)).isEqualTo(1);
    }

    @Test
    void roleChangeAndRemovalTakeEffectImmediately() throws Exception {
        Session owner = registerWithProTenant("st-rm", "移除店");
        Session staff = join(owner, "st-rm1", "STAFF");
        long mid = memberId(owner, staff.userId());

        // 改为管理员：旧令牌立即失效，刷新后拿到新角色
        MockHttpServletResponse role = send(put("/api/v1/members/" + mid + "/role"), owner, "{\"role\":\"ADMIN\"}");
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse stale = send(get("/api/v1/products"), staff, null);
        assertThat(stale.getStatus()).isEqualTo(401);
        assertThat((String) read(stale, "$.code")).isEqualTo("MEMBERSHIP_CHANGED");
        MockHttpServletResponse r1 = refresh(staff);
        assertThat((String) read(r1, "$.currentTenant.role")).isEqualTo("ADMIN");
        Session admin = new Session(staff.email(), staff.password(), read(r1, "$.accessToken"), refreshCookie(r1), staff.userId());
        assertThat(send(get("/api/v1/suppliers"), admin, null).getStatus()).isEqualTo(200);
        // 管理员也不能管理员工
        assertThat(send(get("/api/v1/members"), admin, null).getStatus()).isEqualTo(403);

        // 不能修改或移除店主
        long ownerMid = memberId(owner, owner.userId());
        assertThat(send(post("/api/v1/members/" + ownerMid + "/remove"), owner, null).getStatus()).isEqualTo(409);
        assertThat(send(put("/api/v1/members/" + ownerMid + "/role"), owner, "{\"role\":\"STAFF\"}").getStatus()).isEqualTo(409);

        // 移除：旧令牌立即失效；刷新后没有当前店铺，店铺列表里也没有这家店
        assertThat(send(post("/api/v1/members/" + mid + "/remove"), owner, null).getStatus()).isEqualTo(204);
        assertThat(send(get("/api/v1/products"), admin, null).getStatus()).isEqualTo(401);
        MockHttpServletResponse r2 = refresh(admin);
        assertThat(r2.getStatus()).isEqualTo(200);
        assertThat((Object) read(r2, "$.currentTenant")).isNull();
        assertThat((List<?>) read(r2, "$.tenants")).isEmpty();
        Long tenantId = currentTenantId(owner, mvc);
        assertThat(send(post("/api/v1/tenants/" + tenantId + "/switch"), admin, null).getStatus()).isEqualTo(404);

        // 再次邀请同一个人：复用原成员记录
        String token = invite(owner, "STAFF", "back");
        Session back = new Session(admin.email(), admin.password(), read(r2, "$.accessToken"), refreshCookie(r2), admin.userId());
        assertThat(accept(back, token).getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from tenant_members where tenant_id = ? and user_id = ?",
                Integer.class, tenantId, admin.userId())).isEqualTo(1);
    }

    @Test
    void staffCanLeaveButOwnerCannot() throws Exception {
        Session owner = registerWithProTenant("st-lv", "退出店");
        Session staff = join(owner, "st-lv1", "STAFF");
        Long tenantId = currentTenantId(owner, mvc);

        MockHttpServletResponse left = mvc.perform(withAuth(post("/api/v1/tenants/" + tenantId + "/leave"), staff)
                .cookie(new Cookie("sb_refresh", staff.refreshToken()))).andReturn().getResponse();
        assertThat(left.getStatus()).isEqualTo(200);
        assertThat((Object) read(left, "$.currentTenant")).isNull();
        assertThat((List<?>) read(left, "$.tenants")).isEmpty();
        assertThat(send(get("/api/v1/products"), staff, null).getStatus()).isEqualTo(401);

        MockHttpServletResponse ownerLeave = send(post("/api/v1/tenants/" + tenantId + "/leave"), owner, null);
        assertThat(ownerLeave.getStatus()).isEqualTo(409);
        assertThat((Integer) read(send(get("/api/v1/members"), owner, null), "$.used")).isEqualTo(0);
    }

    @Test
    void proExpiryBlocksStaffButKeepsMembership() throws Exception {
        Session owner = registerWithProTenant("st-exp", "到期店");
        Session staff = join(owner, "st-exp1", "STAFF");
        Long tenantId = currentTenantId(owner, mvc);

        jdbcTemplate.update("update tenants set plan_expires_at = date_sub(utc_timestamp(6), interval 1 minute) where id = ?", tenantId);

        MockHttpServletResponse blocked = send(get("/api/v1/products"), staff, null);
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat((String) read(blocked, "$.code")).isEqualTo("STORE_PLAN_EXPIRED");
        MockHttpServletResponse r = refresh(staff);
        assertThat((Object) read(r, "$.currentTenant")).isNull();
        assertThat((Boolean) read(r, "$.tenants[0].accessible")).isFalse();
        Session out = new Session(staff.email(), staff.password(), read(r, "$.accessToken"), refreshCookie(r), staff.userId());
        assertThat(send(post("/api/v1/tenants/" + tenantId + "/switch"), out, null).getStatus()).isEqualTo(403);
        // 登录时也不会自动进入
        MockHttpServletResponse login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(staff.email(), staff.password())))
                .andReturn().getResponse();
        assertThat((Object) read(login, "$.currentTenant")).isNull();

        // 店主不受影响；邀请被拒（需要专业版）
        assertThat(send(get("/api/v1/products"), owner, null).getStatus()).isEqualTo(200);
        MockHttpServletResponse inv = send(post("/api/v1/invitations"), owner, "{\"role\":\"STAFF\"}");
        assertThat(inv.getStatus()).isEqualTo(403);
        assertThat((String) read(inv, "$.code")).isEqualTo("PLAN_FEATURE_REQUIRED");
        // 成员关系保留
        assertThat((List<?>) read(send(get("/api/v1/members"), owner, null), "$.members")).hasSize(2);

        // 续费后恢复
        jdbcTemplate.update("update tenants set plan_expires_at = date_add(utc_timestamp(6), interval 30 day) where id = ?", tenantId);
        MockHttpServletResponse sw = send(post("/api/v1/tenants/" + tenantId + "/switch"), out, null);
        assertThat(sw.getStatus()).isEqualTo(200);
        assertThat((String) read(sw, "$.currentTenant.role")).isEqualTo("STAFF");
    }

    @Test
    void standardPlanCannotInvite() throws Exception {
        Session owner = registerWithTenant("st-std", "标准版店");
        MockHttpServletResponse res = send(post("/api/v1/invitations"), owner, "{\"role\":\"STAFF\"}");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat((String) read(res, "$.details.requiredPlan")).isEqualTo("LIFETIME");
        MockHttpServletResponse list = send(get("/api/v1/members"), owner, null);
        assertThat((Boolean) read(list, "$.allowed")).isFalse();
        assertThat((Integer) read(list, "$.max")).isEqualTo(0);
    }

    @Test
    void storesAreIsolated() throws Exception {
        Session a = registerWithProTenant("st-a", "A 店");
        Session b = registerWithProTenant("st-b", "B 店");
        Session staffA = join(a, "st-a1", "STAFF");
        invite(a, "STAFF", "pending");
        Number invId = read(send(get("/api/v1/members"), a, null), "$.invitations[0].id");
        long mid = memberId(a, staffA.userId());

        assertThat(send(post("/api/v1/invitations/" + invId + "/revoke"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/members/" + mid + "/remove"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(put("/api/v1/members/" + mid + "/role"), b, "{\"role\":\"ADMIN\"}").getStatus()).isEqualTo(404);
        assertThat((List<?>) read(send(get("/api/v1/members"), b, null), "$.members")).hasSize(1);
        assertThat((List<?>) read(send(get("/api/v1/members"), b, null), "$.invitations")).isEmpty();
        // A 店的员工不在 B 店
        Long bId = currentTenantId(b, mvc);
        assertThat(send(post("/api/v1/tenants/" + bId + "/switch"), staffA, null).getStatus()).isEqualTo(404);
        // 员工仍在 A 店
        assertThat(send(get("/api/v1/products"), staffA, null).getStatus()).isEqualTo(200);
    }

    @Test
    void salesCanBeFilteredByCreator() throws Exception {
        Session owner = registerWithProTenant("st-so", "开单人店");
        Session staff = join(owner, "st-so1", "STAFF");
        long p = product(owner);
        String sale = "{\"priceTier\":\"RETAIL\",\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(p);
        assertThat(send(post("/api/v1/sales-orders"), owner, sale).getStatus()).isEqualTo(201);
        assertThat(send(post("/api/v1/sales-orders"), staff, sale).getStatus()).isEqualTo(201);
        assertThat(send(post("/api/v1/sales-orders"), staff, sale).getStatus()).isEqualTo(201);

        MockHttpServletResponse byStaff = send(get("/api/v1/sales-orders").param("createdBy", staff.userId().toString()), owner, null);
        assertThat((Integer) read(byStaff, "$.totalItems")).isEqualTo(2);
        assertThat((String) read(byStaff, "$.items[0].createdByName")).isEqualTo("st-so1");
        assertThat((Integer) read(send(get("/api/v1/sales-orders"), owner, null), "$.totalItems")).isEqualTo(3);

        List<String> names = read(send(get("/api/v1/sales-orders/creators"), owner, null), "$[*].name");
        assertThat(names).containsExactlyInAnyOrder("st-so", "st-so1");
        assertThat(send(get("/api/v1/sales-orders/creators"), staff, null).getStatus()).isEqualTo(403);
    }
}
