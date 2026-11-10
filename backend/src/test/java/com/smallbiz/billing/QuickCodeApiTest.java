package com.smallbiz.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import com.smallbiz.support.IntegrationTest;

/** 快速发码页（docs/18）：密码正确即取出可兑换的永久会员码；不登录；输错限制；库存用完自动补充。 */
@TestPropertySource(properties = "smallbiz.codes.quick-password=quick-test-pw")
class QuickCodeApiTest extends IntegrationTest {

    private MockHttpServletResponse issue(String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/quick-code").contentType("application/json")
                .content("{\"password\":\"%s\"}".formatted(password)).with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })).andReturn().getResponse();
    }

    @Test
    void correctPasswordIssuesRedeemableSingleUseCode() throws Exception {
        assertThat((Boolean) read(mvc.perform(get("/api/v1/quick-code")).andReturn().getResponse(), "$.enabled")).isTrue();

        MockHttpServletResponse res = issue("quick-test-pw", "10.1.0.1");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        String code = read(res, "$.code");
        assertThat(code).matches("SBZ-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");

        // 每次都是新码
        Set<String> seen = new HashSet<>(Set.of(code));
        for (int i = 0; i < 3; i++) {
            assertThat(seen.add(read(issue("quick-test-pw", "10.1.0.1"), "$.code"))).isTrue();
        }

        // 记在「快速发码」下，状态为已发出
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes where order_ref = ? and code_hint = ?",
                Integer.class, CodeDispenserService.QUICK_ORDER, code.substring(code.length() - 4))).isEqualTo(1);

        // 兑换开通永久会员；同一个码不能再用
        Session buyer = registerWithFreeTenant("quick-buyer", "快速发码买家店");
        MockHttpServletResponse redeemed = send(post("/api/v1/billing/redeem"), buyer, "{\"code\":\"%s\"}".formatted(code));
        assertThat(redeemed.getStatus()).as(redeemed.getContentAsString()).isEqualTo(200);
        assertThat((Boolean) read(redeemed, "$.lifetime")).isTrue();
        Session other = registerWithFreeTenant("quick-buyer2", "第二家店");
        MockHttpServletResponse again = send(post("/api/v1/billing/redeem"), other, "{\"code\":\"%s\"}".formatted(code));
        assertThat(again.getStatus()).isNotEqualTo(200);
    }

    @Test
    void wrongPasswordIsRejectedAndLockedAfterFiveTries() throws Exception {
        long before = jdbcTemplate.queryForObject("select count(*) from activation_codes where order_ref = ?", Long.class,
                CodeDispenserService.QUICK_ORDER);
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = issue("wrong", "10.2.0.2");
            assertThat(res.getStatus()).isEqualTo(401);
            assertThat((String) read(res, "$.code")).isEqualTo("WRONG_PASSWORD");
        }
        // 锁定后连正确密码也不行；其他 IP 不受影响
        assertThat(issue("quick-test-pw", "10.2.0.2").getStatus()).isEqualTo(429);
        assertThat(issue("quick-test-pw", "10.2.0.3").getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes where order_ref = ?", Long.class,
                CodeDispenserService.QUICK_ORDER)).isEqualTo(before + 1);
    }

    @Test
    void refillsAutomaticallyWhenStockIsEmpty() throws Exception {
        jdbcTemplate.update("update activation_codes c join activation_code_batches b on b.id = c.batch_id "
                + "set c.status = 'VOIDED' where b.lifetime = true and c.status = 'UNUSED' and c.dispensed_at is null");
        MockHttpServletResponse res = issue("quick-test-pw", "10.3.0.3");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("select count(*) from activation_codes c join activation_code_batches b on b.id = c.batch_id "
                + "where b.name = ? and c.status = 'UNUSED' and c.dispensed_at is null", Integer.class,
                CodeDispenserService.QUICK_REFILL_BATCH)).isEqualTo(CodeDispenserService.QUICK_REFILL_SIZE - 1);
    }
}
