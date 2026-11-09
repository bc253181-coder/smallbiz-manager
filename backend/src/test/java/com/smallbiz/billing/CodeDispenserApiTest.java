package com.smallbiz.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;

/** 发码台（docs/17）：取码不重复、并发、库存、同单确认、查看、作废、加密、权限。 */
class CodeDispenserApiTest extends IntegrationTest {

    @Autowired
    CodeCrypto crypto;

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

    /** 测试之间共用一个数据库：每个测试先把可发的码清空，再生成自己需要的数量。 */
    private void onlyFreshCodes(Session admin, int n) throws Exception {
        jdbcTemplate.update("update activation_codes c join activation_code_batches b on b.id = c.batch_id "
                + "set c.status = 'VOIDED' where b.lifetime = true and c.status = 'UNUSED' and c.dispensed_at is null");
        MockHttpServletResponse res = send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"发码台测试\",\"plan\":\"PRO\",\"durationDays\":0,\"trial\":false,\"lifetime\":true,\"quantity\":%d}".formatted(n));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
    }

    private MockHttpServletResponse dispense(Session admin, String order, int qty, boolean confirm) throws Exception {
        return send(post("/api/v1/admin/code-dispenser/dispense"), admin,
                "{\"orderRef\":\"%s\",\"quantity\":%d,\"confirmRepeat\":%s}".formatted(order, qty, confirm));
    }

    @Test
    void dispensedCodeRedeemsAndIsTracked() throws Exception {
        Session admin = admin();
        onlyFreshCodes(admin, 3);
        assertThat(((Number) read(send(get("/api/v1/admin/code-dispenser/stock"), admin, null), "$.available")).intValue()).isEqualTo(3);

        MockHttpServletResponse res = dispense(admin, "XHS-1001", 1, false);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        String code = read(res, "$[0].code");
        long id = ((Number) read(res, "$[0].id")).longValue();
        assertThat(code).matches("SBZ-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(((Number) read(send(get("/api/v1/admin/code-dispenser/stock"), admin, null), "$.available")).intValue()).isEqualTo(2);

        // 数据库里没有原码（只有哈希和加密内容）
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes where code_encrypted like ?", Integer.class,
                "%" + code + "%")).isZero();

        // 列表：已发出，带订单号和操作人
        MockHttpServletResponse rows = send(get("/api/v1/admin/code-dispenser/codes?q=XHS-1001"), admin, null);
        assertThat((String) read(rows, "$[0].state")).isEqualTo("DISPENSED");
        assertThat((String) read(rows, "$[0].orderRef")).isEqualTo("XHS-1001");
        assertThat((String) read(rows, "$[0].dispensedByName")).isEqualTo("平台");

        // 买家兑换成功 → 已兑换，显示店铺
        Session buyer = registerWithFreeTenant("disp-buyer", "发码买家店");
        MockHttpServletResponse redeemed = send(post("/api/v1/billing/redeem"), buyer, "{\"code\":\"%s\"}".formatted(code));
        assertThat(redeemed.getStatus()).as(redeemed.getContentAsString()).isEqualTo(200);
        assertThat((Boolean) read(redeemed, "$.lifetime")).isTrue();
        rows = send(get("/api/v1/admin/code-dispenser/codes?q=" + code.substring(code.length() - 4)), admin, null);
        List<String> states = read(rows, "$[?(@.id == " + id + ")].state");
        assertThat(states).containsExactly("REDEEMED");
        List<String> stores = read(rows, "$[?(@.id == " + id + ")].redeemedTenantName");
        assertThat(stores).containsExactly("发码买家店");

        // 再看一次：和发出去的一样，并记录
        assertThat((String) read(send(post("/api/v1/admin/code-dispenser/codes/" + id + "/reveal"), admin, null), "$.code"))
                .isEqualTo(code);
        assertThat(jdbcTemplate.queryForObject("select group_concat(action order by id) from code_dispense_logs where code_id = ?",
                String.class, id)).isEqualTo("DISPENSE,REVEAL");
    }

    @Test
    void sameOrderNeedsConfirmation_andNotEnoughCodesDispensesNothing() throws Exception {
        Session admin = admin();
        onlyFreshCodes(admin, 3);
        assertThat(dispense(admin, "XHS-2002", 1, false).getStatus()).isEqualTo(200);
        MockHttpServletResponse again = dispense(admin, "XHS-2002", 1, false);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat((String) read(again, "$.code")).isEqualTo("ORDER_ALREADY_DISPENSED");
        assertThat((Integer) read(again, "$.details.count")).isEqualTo(1);
        assertThat(dispense(admin, "XHS-2002", 1, true).getStatus()).isEqualTo(200);

        // 只剩 1 个，要 2 个：一个也不发
        MockHttpServletResponse short2 = dispense(admin, "XHS-2003", 2, false);
        assertThat((String) read(short2, "$.code")).isEqualTo("NOT_ENOUGH_CODES");
        assertThat((Integer) read(short2, "$.details.available")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes where order_ref = 'XHS-2003'", Integer.class))
                .isZero();
        assertThat((Boolean) read(send(get("/api/v1/admin/code-dispenser/stock"), admin, null), "$.low")).isTrue();
        assertThat(dispense(admin, " ", 1, false).getStatus()).isEqualTo(400);
    }

    @Test
    void concurrentDispensingNeverHandsOutTheSameCode() throws Exception {
        Session admin = admin();
        onlyFreshCodes(admin, 10);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                go.await();
                return dispense(admin, "XHS-C" + n, 1, false);
            }));
        }
        go.countDown();
        Set<String> codes = new HashSet<>();
        int ok = 0;
        for (Future<MockHttpServletResponse> f : results) {
            MockHttpServletResponse r = f.get();
            if (r.getStatus() == 200) {
                ok++;
                codes.add(read(r, "$[0].code"));
            } else {
                assertThat(r.getContentAsString()).isEmpty(); // 不应该出现
            }
        }
        pool.shutdown();
        assertThat(codes).hasSize(ok);
        assertThat(ok).isEqualTo(10); // 10 个码、10 个请求：每个请求都拿到不同的码
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes where order_ref like 'XHS-C%'", Integer.class))
                .isEqualTo(ok);
    }

    @Test
    void voidOrderBlocksUnusedCodes_redeemedOnesAreReported() throws Exception {
        Session admin = admin();
        onlyFreshCodes(admin, 2);
        MockHttpServletResponse res = dispense(admin, "XHS-3003", 2, false);
        String first = read(res, "$[0].code");
        String second = read(res, "$[1].code");
        Session buyer = registerWithFreeTenant("disp-void", "退款店");
        assertThat(send(post("/api/v1/billing/redeem"), buyer, "{\"code\":\"%s\"}".formatted(first)).getStatus()).isEqualTo(200);

        MockHttpServletResponse v = send(post("/api/v1/admin/code-dispenser/void-order"), admin, "{\"orderRef\":\"XHS-3003\"}");
        assertThat((Integer) read(v, "$.voided")).isEqualTo(1);
        assertThat((List<String>) read(v, "$.alreadyRedeemed")).containsExactly("****" + first.substring(first.length() - 4));
        Session other = registerWithFreeTenant("disp-void2", "拿到作废码的店");
        assertThat((String) read(send(post("/api/v1/billing/redeem"), other, "{\"code\":\"%s\"}".formatted(second)), "$.code"))
                .isEqualTo("CODE_VOIDED");
        assertThat(send(post("/api/v1/admin/code-dispenser/void-order"), admin, "{\"orderRef\":\"NO-SUCH\"}").getStatus())
                .isEqualTo(404);
    }

    @Test
    void oldCodesWithoutStoredTextAreNotDispensed_andOnlyAdminsCanUseIt() throws Exception {
        Session admin = admin();
        onlyFreshCodes(admin, 1);
        // 模拟以前生成的旧码：没有加密原文
        jdbcTemplate.update("update activation_codes set code_encrypted = null where status = 'UNUSED' and dispensed_at is null");
        assertThat((String) read(dispense(admin, "XHS-OLD", 1, false), "$.code")).isEqualTo("NOT_ENOUGH_CODES");

        Session merchant = registerWithFreeTenant("disp-perm", "普通店");
        assertThat(send(get("/api/v1/admin/code-dispenser/stock"), merchant, null).getStatus()).isEqualTo(403);
        assertThat(dispense(merchant, "XHS-HACK", 1, false).getStatus()).isEqualTo(403);
    }

    @Test
    void encryptionRoundTripsAndRejectsTampering() {
        byte[] enc = crypto.encrypt("SBZ-ABCD-EFGH-JKLM");
        assertThat(crypto.decrypt(enc)).isEqualTo("SBZ-ABCD-EFGH-JKLM");
        assertThat(crypto.encrypt("SBZ-ABCD-EFGH-JKLM")).isNotEqualTo(enc); // 每次随机 IV
        enc[enc.length - 1] ^= 1;
        byte[] tampered = enc;
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        CodeCrypto wrongKey = new CodeCrypto("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        byte[] good = crypto.encrypt("SBZ-ABCD-EFGH-JKLM");
        assertThatThrownBy(() -> wrongKey.decrypt(good)).isInstanceOf(IllegalStateException.class);
        assertThat(new CodeCrypto("").available()).isFalse();
    }
}
