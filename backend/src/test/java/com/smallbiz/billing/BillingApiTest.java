package com.smallbiz.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.smallbiz.support.IntegrationTest;

class BillingApiTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1};

    /** 平台管理员：邮箱在 application-test.yml 的 admin-emails 中。 */
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

    private List<String> createCodes(Session admin, String plan, int days, boolean trial, int qty) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/admin/code-batches"), admin, """
                {"name":"测试批次","plan":"%s","durationDays":%d,"trial":%s,"quantity":%d}
                """.formatted(plan, days, trial, qty));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return read(res, "$.codes");
    }

    private MockHttpServletResponse redeem(Session s, String code) throws Exception {
        return send(post("/api/v1/billing/redeem"), s, "{\"code\":\"%s\"}".formatted(code));
    }

    private Instant expires(Session s) throws Exception {
        return Instant.parse(read(send(get("/api/v1/billing/current"), s, null), "$.expiresAt"));
    }

    // ---------------- 免费版限制 ----------------

    @Test
    void freePlanRestrictsPaidFeatures_butWalkInRetailSalesWork() throws Exception {
        Session s = registerWithFreeTenant("bill-free", "免费店");
        MockHttpServletResponse cur = send(get("/api/v1/billing/current"), s, null);
        assertThat((String) read(cur, "$.plan")).isEqualTo("FREE");
        assertThat((Integer) read(cur, "$.maxProducts")).isEqualTo(3);
        assertThat((String) read(cur, "$.membership.membership")).isEqualTo("FREE");
        assertThat((Integer) read(cur, "$.membership.storesMax")).isEqualTo(2);
        assertThat((Integer) read(cur, "$.membership.storesUsed")).isEqualTo(1);
        assertThat((Boolean) read(cur, "$.features.PURCHASING")).isFalse();

        MockHttpServletResponse customers = send(get("/api/v1/customers"), s, null);
        assertThat(customers.getStatus()).isEqualTo(403);
        assertThat((String) read(customers, "$.code")).isEqualTo("PLAN_FEATURE_REQUIRED");
        assertThat((String) read(customers, "$.details.requiredPlan")).isEqualTo("LIFETIME");
        assertThat(send(get("/api/v1/suppliers"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/purchase-orders"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/reports/sales/export"), s, null).getStatus()).isEqualTo(403);
        // 高级数据分析（趋势、排行、毛利）默认不开放；今日 / 本月合计和库存报表可以看
        assertThat((String) read(send(get("/api/v1/reports/sales"), s, null), "$.code")).isEqualTo("PLAN_FEATURE_REQUIRED");
        assertThat(send(get("/api/v1/dashboard/sales-trend"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/reports/top-products"), s, null).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/v1/dashboard/summary"), s, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/reports/inventory"), s, null).getStatus()).isEqualTo(200);

        Long p = ((Number) read(send(post("/api/v1/products"), s, """
                {"name":"商品","unit":"个","costPrice":"1","retailPrice":"5","wholesalePrice":"3","lowStockThreshold":0,"initialStock":10}
                """), "$.id")).longValue();
        assertThat(send(post("/api/v1/sales-orders"), s, """
                {"priceTier":"RETAIL","items":[{"productId":%d,"quantity":1}]}
                """.formatted(p)).getStatus()).isEqualTo(201);
        assertThat((String) read(send(post("/api/v1/sales-orders"), s, """
                {"priceTier":"WHOLESALE","items":[{"productId":%d,"quantity":1}]}
                """.formatted(p)), "$.details.feature")).isEqualTo("WHOLESALE_PRICE");
    }

    @Test
    void freePlanProductLimit_countsPerAccount_deleteDoesNotRefund_upgradeLifts() throws Exception {
        Session s = registerWithFreeTenant("bill-limit", "限额店");
        String body = "{\"name\":\"第%d个\",\"unit\":\"个\",\"costPrice\":\"1\",\"retailPrice\":\"2\",\"lowStockThreshold\":0,\"initialStock\":0}";
        long first = 0;
        for (int i = 1; i <= 3; i++) {
            MockHttpServletResponse ok = send(post("/api/v1/products"), s, body.formatted(i));
            assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(201);
            if (i == 1) {
                first = ((Number) read(ok, "$.id")).longValue();
            }
        }
        MockHttpServletResponse over = send(post("/api/v1/products"), s, body.formatted(4));
        assertThat(over.getStatus()).isEqualTo(403);
        assertThat((String) read(over, "$.code")).isEqualTo("PLAN_LIMIT_REACHED");
        assertThat((String) read(over, "$.details.limit")).isEqualTo("products");
        assertThat((Integer) read(over, "$.details.max")).isEqualTo(3);

        // 已有商品照常查看、修改
        assertThat(send(get("/api/v1/products/" + first), s, null).getStatus()).isEqualTo(200);
        // 删除不返还次数
        assertThat(send(delete("/api/v1/products/" + first), s, null).getStatus()).isEqualTo(204);
        assertThat(send(post("/api/v1/products"), s, body.formatted(5)).getStatus()).isEqualTo(403);
        // 次数按账号累计：同一账号新开的第二家店也不能再建
        Session s2 = createTenant(s, "限额店二");
        assertThat(send(post("/api/v1/products"), s2, body.formatted(6)).getStatus()).isEqualTo(403);
        assertThat((Integer) read(send(get("/api/v1/billing/current"), s2, null), "$.membership.productsUsed")).isEqualTo(3);

        // 升级永久会员后立即解除限制，名下两家店都是高级会员
        Session admin = admin();
        assertThat(send(post("/api/v1/admin/users/" + s.userId() + "/membership"), admin,
                "{\"membership\":\"LIFETIME\",\"note\":\"测试\"}").getStatus()).isEqualTo(200);
        assertThat(send(post("/api/v1/products"), s2, body.formatted(7)).getStatus()).isEqualTo(201);
        MockHttpServletResponse cur = send(get("/api/v1/billing/current"), s2, null);
        assertThat((Boolean) read(cur, "$.lifetime")).isTrue();
        assertThat((String) read(cur, "$.plan")).isEqualTo("PRO");
        assertThat((Object) read(cur, "$.expiresAt")).isNull();
        assertThat((Object) read(cur, "$.maxProducts")).isNull();
        assertThat(send(get("/api/v1/customers"), s2, null).getStatus()).isEqualTo(200);
    }
    // ---------------- 激活码 ----------------

    @Test
    void activationCodesGrantExtendAndCannotBeReused() throws Exception {
        Session admin = admin();
        List<String> trial = createCodes(admin, "STANDARD", 30, true, 2);
        List<String> month = createCodes(admin, "STANDARD", 30, false, 2);
        assertThat(trial.get(0)).matches("SBZ-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");

        Session a = registerWithFreeTenant("bill-code-a", "激活A");
        // 小写、多余空格、无横线也能识别
        MockHttpServletResponse ok = redeem(a, "  " + trial.get(0).toLowerCase().replace("-", "") + " ");
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat((String) read(ok, "$.plan")).isEqualTo("STANDARD");
        assertThat((Boolean) read(ok, "$.trialUsed")).isTrue();
        Instant first = expires(a);
        assertThat(Duration.between(Instant.now(), first).toDays()).isBetween(29L, 30L);
        assertThat(send(get("/api/v1/purchase-orders"), a, null).getStatus()).isEqualTo(200); // 付费功能已开放

        // 首月体验每店限一次
        assertThat((String) read(redeem(a, trial.get(1)), "$.code")).isEqualTo("TRIAL_ALREADY_USED");
        // 同套餐续期：从原到期时间顺延 30 天
        redeem(a, month.get(0));
        assertThat(Duration.between(first, expires(a)).toDays()).isEqualTo(30L);

        // 已使用的码不能被其他店铺使用
        Session b = registerWithFreeTenant("bill-code-b", "激活B");
        assertThat((String) read(redeem(b, trial.get(0)), "$.code")).isEqualTo("CODE_USED");
        assertThat((String) read(redeem(b, "SBZ-AAAA-BBBB-CCCC"), "$.code")).isEqualTo("CODE_INVALID");

        // 作废后不能兑换
        assertThat(send(post("/api/v1/admin/codes/void"), admin, "{\"code\":\"%s\"}".formatted(month.get(1))).getStatus())
                .isEqualTo(200);
        assertThat((String) read(redeem(b, month.get(1)), "$.code")).isEqualTo("CODE_VOIDED");
        assertThat((String) read(send(get("/api/v1/billing/current"), b, null), "$.plan")).isEqualTo("FREE");

        MockHttpServletResponse history = send(get("/api/v1/billing/history"), a, null);
        assertThat((List<?>) read(history, "$")).hasSize(2);
        assertThat((String) read(history, "$[1].source")).isEqualTo("ACTIVATION_CODE");
    }

    @Test
    void sameCodeRedeemedConcurrentlyByTwoStores_onlyOneSucceeds() throws Exception {
        Session admin = admin();
        String code = createCodes(admin, "STANDARD", 30, false, 1).get(0);
        List<Session> stores = List.of(registerWithFreeTenant("bill-cc-a", "并发A"), registerWithFreeTenant("bill-cc-b", "并发B"),
                registerWithFreeTenant("bill-cc-c", "并发C"), registerWithFreeTenant("bill-cc-d", "并发D"));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(stores.size());
        try {
            List<java.util.concurrent.Future<Integer>> results = stores.stream().map(s -> pool.submit(() -> {
                start.await();
                return redeem(s, code).getStatus();
            })).toList();
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (var f : results) {
                statuses.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(st -> st == 200).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(st -> st == 409 || st == 400).count()).isEqualTo(stores.size() - 1);
        } finally {
            pool.shutdownNow();
        }
        Integer paid = jdbcTemplate.queryForObject(
                "select count(*) from tenants where name in ('并发A','并发B','并发C','并发D') and plan = 'STANDARD'", Integer.class);
        assertThat(paid).isEqualTo(1);
    }

    @Test
    void redeemIsRateLimited() throws Exception {
        Session s = registerWithFreeTenant("bill-rate", "限速店");
        for (int i = 0; i < 10; i++) {
            assertThat(redeem(s, "SBZ-WRNG-CODE-%04d".formatted(i)).getStatus()).isEqualTo(400);
        }
        assertThat(redeem(s, "SBZ-WRNG-CODE-9999").getStatus()).isEqualTo(429);
    }

    @Test
    void onlyPlatformAdminCanUseAdminApi_andOnlyOwnerCanRedeem() throws Exception {
        Session merchant = registerWithFreeTenant("bill-perm", "普通店");
        assertThat(send(get("/api/v1/admin/stats"), merchant, null).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/v1/admin/code-batches"), merchant,
                "{\"name\":\"x\",\"plan\":\"STANDARD\",\"durationDays\":30,\"trial\":false,\"quantity\":1}").getStatus())
                .isEqualTo(403);
        Session admin = admin();
        assertThat(send(get("/api/v1/admin/stats"), admin, null).getStatus()).isEqualTo(200);
        assertThat((Boolean) read(send(get("/api/v1/auth/me"), admin, null), "$.user.platformAdmin")).isTrue();
    }

    @Test
    void expiredPlanFallsBackToFreeAndKeepsData() throws Exception {
        Session s = registerWithTenant("bill-exp", "到期店");
        Long tenantId = currentTenantId(s, mvc);
        assertThat(send(post("/api/v1/customers"), s, "{\"name\":\"老客户\",\"customerType\":\"INDIVIDUAL\",\"priceTier\":\"RETAIL\"}")
                .getStatus()).isEqualTo(201);
        jdbcTemplate.update("update tenants set plan_expires_at = date_sub(utc_timestamp(6), interval 1 day) where id = ?", tenantId);

        assertThat((String) read(send(get("/api/v1/billing/current"), s, null), "$.plan")).isEqualTo("FREE");
        assertThat(send(get("/api/v1/customers"), s, null).getStatus()).isEqualTo(403);
        Integer count = jdbcTemplate.queryForObject("select count(*) from customers where tenant_id = ?", Integer.class, tenantId);
        assertThat(count).isEqualTo(1); // 数据仍在
    }

    // ---------------- 价格（V18：只卖高级永久会员） ----------------

    @Test
    void onlyLifetimeMembershipIsForSale() throws Exception {
        Session s = registerWithFreeTenant("bill-price", "价格店");
        MockHttpServletResponse cny = send(get("/api/v1/billing/prices?currency=CNY"), s, null);
        assertThat((List<String>) read(cny, "$[*].amount")).containsExactly("29.90");
        assertThat((List<String>) read(cny, "$[*].period")).containsExactly("LIFETIME");
        MockHttpServletResponse myr = send(get("/api/v1/billing/prices?currency=MYR"), s, null);
        assertThat((List<String>) read(myr, "$[*].amount")).containsExactly("19.90");

        // 以前的订阅价格保留在后台（已停售）
        Session admin = admin();
        MockHttpServletResponse all = send(get("/api/v1/admin/plan-prices"), admin, null);
        assertThat((List<Boolean>) read(all, "$[?(@.period != 'LIFETIME')].enabled")).isNotEmpty().allMatch(e -> !e);
    }
    // ---------------- 扫码付款 ----------------

    @Test
    void codeOnlyByDefault_paymentOrdersAreRefused() throws Exception {
        // V19：网站只用激活码开通，收款方式全部停用
        Session owner = registerWithFreeTenant("bill-code-only", "只用激活码店");
        assertThat((List<?>) read(send(get("/api/v1/billing/payment-methods"), owner, null), "$")).isEmpty();
        List<Integer> lifetime = read(send(get("/api/v1/billing/prices?currency=CNY"), owner, null), "$[?(@.period == 'LIFETIME')].id");
        Integer anyMethod = jdbcTemplate.queryForObject("select min(id) from payment_methods", Integer.class);
        assertThat((String) read(send(post("/api/v1/billing/payment-orders"), owner,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(lifetime.get(0), anyMethod)), "$.code"))
                .isEqualTo("METHOD_NOT_FOUND");
    }

    /** 扫码付款（V19 起默认停用，平台后台可以重新启用）：启用后流程照常。 */
    @Test
    void manualPaymentFlow() throws Exception {
        jdbcTemplate.update("update payment_methods set enabled = true");
        try {
            manualPaymentFlowWithMethodsEnabled();
        } finally {
            jdbcTemplate.update("update payment_methods set enabled = false");
        }
    }

    private void manualPaymentFlowWithMethodsEnabled() throws Exception {
        Session owner = registerWithFreeTenant("bill-pay", "付款店");
        Session admin = admin();

        List<Integer> cnyIds = read(send(get("/api/v1/billing/prices?currency=CNY"), owner, null), "$[?(@.period == 'LIFETIME')].id");
        List<Integer> myrIds = read(send(get("/api/v1/billing/prices?currency=MYR"), owner, null), "$[?(@.period == 'LIFETIME')].id");
        assertThat((List<?>) read(send(get("/api/v1/billing/prices?currency=CNY"), owner, null), "$[?(@.plan == 'PRO' && @.period == 'TRIAL_MONTH')]")).isEmpty();
        MockHttpServletResponse methods = send(get("/api/v1/billing/payment-methods"), owner, null);
        List<Integer> wechat = read(methods, "$[?(@.code == 'WECHAT_PAY')].id");
        List<String> qr = read(methods, "$[?(@.code == 'WECHAT_PAY')].qrImageUrl");
        assertThat(qr.get(0)).startsWith("/uploads/platform/"); // 首次启动已导入收款码

        // 币种不匹配
        assertThat((String) read(send(post("/api/v1/billing/payment-orders"), owner,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(myrIds.get(0), wechat.get(0))), "$.code"))
                .isEqualTo("CURRENCY_MISMATCH");

        MockHttpServletResponse created = send(post("/api/v1/billing/payment-orders"), owner,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(cnyIds.get(0), wechat.get(0)));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        assertThat((String) read(created, "$.amount")).isEqualTo("29.90");
        assertThat((String) read(created, "$.referenceCode")).matches("SBZ-[A-Z2-9]{4}");
        assertThat((String) read(created, "$.status")).isEqualTo("AWAITING_PAYMENT");
        Long id = ((Number) read(created, "$.id")).longValue();

        // 同时只能有一张未完成的付款单
        assertThat((String) read(send(post("/api/v1/billing/payment-orders"), owner,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(cnyIds.get(0), wechat.get(0))), "$.code"))
                .isEqualTo("PAYMENT_IN_PROGRESS");

        // 其他店铺看不到、不能提交凭证
        Session other = registerWithFreeTenant("bill-pay-x", "别家店");
        assertThat(send(get("/api/v1/billing/payment-orders/" + id), other, null).getStatus()).isEqualTo(404);
        assertThat(uploadProof(other, id, "TXN-OTHER").getStatus()).isEqualTo(404);

        MockHttpServletResponse submitted = uploadProof(owner, id, "WX-202611090001");
        assertThat(submitted.getStatus()).as(submitted.getContentAsString()).isEqualTo(200);
        assertThat((String) read(submitted, "$.status")).isEqualTo("PENDING_REVIEW");
        assertThat(send(get("/api/v1/billing/payment-orders/" + id + "/proof-image"), owner, null).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/v1/billing/payment-orders/" + id + "/proof-image"), other, null).getStatus()).isEqualTo(404);

        // 管理员审核
        List<Integer> pending = read(send(get("/api/v1/admin/payment-orders?status=PENDING_REVIEW"), admin, null), "$[*].id");
        assertThat(pending).contains(id.intValue());
        assertThat(send(get("/api/v1/admin/payment-orders/" + id + "/proof-image"), admin, null).getStatus()).isEqualTo(200);
        assertThat(send(post("/api/v1/admin/payment-orders/" + id + "/reject"), admin, "{\"reason\":\" \"}").getStatus())
                .isEqualTo(400);
        MockHttpServletResponse approved = send(post("/api/v1/admin/payment-orders/" + id + "/approve"), admin, null);
        assertThat((String) read(approved, "$.status")).isEqualTo("APPROVED");
        assertThat(send(post("/api/v1/admin/payment-orders/" + id + "/approve"), admin, null).getStatus()).isEqualTo(409);

        MockHttpServletResponse cur = send(get("/api/v1/billing/current"), owner, null);
        assertThat((String) read(cur, "$.plan")).isEqualTo("PRO");
        assertThat((Boolean) read(cur, "$.lifetime")).isTrue();
        assertThat((String) read(cur, "$.membership.membership")).isEqualTo("LIFETIME");
        assertThat((String) read(send(get("/api/v1/billing/history"), owner, null), "$[0].source")).isEqualTo("MANUAL_PAYMENT");

        // 已经是永久会员不能再买；同一交易单号不能再次提交
        assertThat((String) read(send(post("/api/v1/billing/payment-orders"), owner,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(cnyIds.get(0), wechat.get(0))), "$.code"))
                .isEqualTo("ALREADY_LIFETIME");
        MockHttpServletResponse otherOrder = send(post("/api/v1/billing/payment-orders"), other,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(cnyIds.get(0), wechat.get(0)));
        Long otherId = ((Number) read(otherOrder, "$.id")).longValue();
        assertThat((String) read(uploadProof(other, otherId, "WX-202611090001"), "$.code")).isEqualTo("TRANSACTION_REF_USED");

        // 拒绝需填原因；取消待付款单
        assertThat(send(post("/api/v1/billing/payment-orders/" + otherId + "/cancel"), other, null).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse uploadProof(Session s, Long id, String txn) throws Exception {
        return mvc.perform(multipart("/api/v1/billing/payment-orders/" + id + "/proof")
                .file(new MockMultipartFile("file", "proof.png", "image/png", PNG))
                .param("transactionRef", txn)
                .header("Authorization", "Bearer " + s.accessToken())).andReturn().getResponse();
    }

    // ---------------- 管理员手动调整 ----------------

    @Test
    void adminCanSetPlanManuallyWithReason() throws Exception {
        Session admin = admin();
        Session s = registerWithFreeTenant("bill-manual", "手动店");
        Long tenantId = currentTenantId(s, mvc);
        String until = java.time.LocalDate.now().plusDays(10).toString();
        assertThat(send(post("/api/v1/admin/tenants/" + tenantId + "/plan"), admin,
                "{\"plan\":\"STANDARD\",\"expiresOn\":\"%s\",\"note\":\" \"}".formatted(until)).getStatus()).isEqualTo(400);
        MockHttpServletResponse ok = send(post("/api/v1/admin/tenants/" + tenantId + "/plan"), admin,
                "{\"plan\":\"STANDARD\",\"expiresOn\":\"%s\",\"note\":\"补偿\"}".formatted(until));
        assertThat((String) read(ok, "$.plan")).isEqualTo("STANDARD");
        List<String> owners = read(send(get("/api/v1/admin/tenants?q=手动店"), admin, null), "$[*].ownerEmail");
        assertThat(owners).containsExactly(s.email());
    }
}
