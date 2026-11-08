package com.smallbiz.receivable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.support.IntegrationTest;

/**
 * 应收账款：赊账、先欠先还、指定单收款、作废、取消规则、并发、对账单、账龄、套餐与店铺隔离。
 * 所有金额均为手算（商品零售价 10 元）。
 */
class ReceivableApiTest extends IntegrationTest {

    // ---------------- 辅助 ----------------

    private long product(Session s) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"应收测试品 %s","unit":"个","costPrice":"5","retailPrice":"10","lowStockThreshold":0,"initialStock":1000}
                """.formatted(System.nanoTime()));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private long customer(Session s, String name) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/customers"), s,
                "{\"name\":\"%s\",\"customerType\":\"BUSINESS\",\"priceTier\":\"RETAIL\"}".formatted(name));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    /** qty 件 × 10 元；paid 为 null 表示全额。 */
    private MockHttpServletResponse sale(Session s, Long customerId, long productId, int qty, String paid) throws Exception {
        String customerJson = customerId == null ? "null" : customerId.toString();
        String paidJson = paid == null ? "null" : "\"" + paid + "\"";
        return send(post("/api/v1/sales-orders"), s, """
                {"customerId":%s,"priceTier":"RETAIL","paidAmount":%s,"items":[{"productId":%d,"quantity":%d}]}
                """.formatted(customerJson, paidJson, productId, qty));
    }

    private long saleId(MockHttpServletResponse res) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) read(res, "$.id")).longValue();
    }

    private MockHttpServletResponse pay(Session s, long customerId, String amount, Long orderId) throws Exception {
        return send(post("/api/v1/customer-payments"), s, """
                {"customerId":%d,"amount":"%s","method":"WECHAT","salesOrderId":%s}
                """.formatted(customerId, amount, orderId == null ? "null" : orderId.toString()));
    }

    private String outstanding(Session s, long customerId) throws Exception {
        return read(send(get("/api/v1/receivables/customers/" + customerId), s, null), "$.outstanding");
    }

    private MockHttpServletResponse order(Session s, long id) throws Exception {
        return send(get("/api/v1/sales-orders/" + id), s, null);
    }

    private Session admin() throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\"}")).andReturn().getResponse();
        if (res.getStatus() != 200) {
            res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@platform.test\",\"password\":\"AdminPass123\",\"displayName\":\"平台\",\"agreeTerms\":true}"))
                    .andReturn().getResponse();
        }
        return new Session("admin@platform.test", "AdminPass123", read(res, "$.accessToken"), null,
                ((Number) read(res, "$.user.id")).longValue());
    }

    /** 每张单：已收 = 开单实收 + 有效冲抵；每笔收款：金额 = 冲抵合计。 */
    private void assertInvariants(Session s) throws Exception {
        Long tenantId = currentTenantId(s, mvc);
        Integer badOrders = jdbcTemplate.queryForObject("""
                select count(*) from sales_orders o
                where o.tenant_id = ? and o.paid_amount <> o.paid_at_sale + coalesce((
                    select sum(a.amount) from payment_allocations a join customer_payments p on p.id = a.payment_id
                    where a.sales_order_id = o.id and p.status = 'VALID'), 0)
                   or (o.tenant_id = ? and (o.paid_amount < 0 or o.paid_amount > o.total_amount))
                """, Integer.class, tenantId, tenantId);
        assertThat(badOrders).isZero();
        Integer badPayments = jdbcTemplate.queryForObject("""
                select count(*) from customer_payments p
                where p.tenant_id = ? and p.amount <> (select coalesce(sum(a.amount), 0) from payment_allocations a
                                                        where a.payment_id = p.id)
                """, Integer.class, tenantId);
        assertThat(badPayments).isZero();
    }

    // ---------------- 赊账开单 ----------------

    @Test
    void creditSaleRecordsDebtOnlyForCustomers() throws Exception {
        Session s = registerWithProTenant("ar-credit", "赊账店");
        long p = product(s);
        long c = customer(s, "老王");

        long partial = saleId(sale(s, c, p, 10, "30"));
        MockHttpServletResponse o = order(s, partial);
        assertThat((String) read(o, "$.totalAmount")).isEqualTo("100.00");
        assertThat((String) read(o, "$.paidAmount")).isEqualTo("30.00");
        assertThat((String) read(o, "$.paidAtSale")).isEqualTo("30.00");
        assertThat((String) read(o, "$.outstanding")).isEqualTo("70.00");
        assertThat((String) read(o, "$.paymentStatus")).isEqualTo("PARTIAL");

        long unpaid = saleId(sale(s, c, p, 2, "0"));
        assertThat((String) read(order(s, unpaid), "$.paymentStatus")).isEqualTo("UNPAID");
        long full = saleId(sale(s, c, p, 1, null));
        assertThat((String) read(order(s, full), "$.paymentStatus")).isEqualTo("PAID");

        assertThat((String) read(sale(s, null, p, 1, "5"), "$.code")).isEqualTo("CREDIT_REQUIRES_CUSTOMER");
        assertThat((String) read(sale(s, c, p, 1, "10.01"), "$.code")).isEqualTo("PAID_EXCEEDS_TOTAL");
        assertThat(saleId(sale(s, null, p, 1, "10"))).isPositive(); // 散客全额可以

        assertThat(outstanding(s, c)).isEqualTo("90.00");
        assertInvariants(s);
    }

    @Test
    void standardPlanCannotUseReceivables() throws Exception {
        Session s = registerWithTenant("ar-std", "标准店");
        long p = product(s);
        long c = customer(s, "客户");
        MockHttpServletResponse res = sale(s, c, p, 1, "0");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat((String) read(res, "$.code")).isEqualTo("PLAN_FEATURE_REQUIRED");
        assertThat((String) read(res, "$.details.requiredPlan")).isEqualTo("PRO");
        assertThat((String) read(res, "$.message")).contains("专业版");
        assertThat(send(get("/api/v1/receivables/summary"), s, null).getStatus()).isEqualTo(403);
        assertThat(pay(s, c, "1", null).getStatus()).isEqualTo(403);
        assertThat(saleId(sale(s, c, p, 1, null))).isPositive(); // 全额收款照常
    }

    // ---------------- 收款 ----------------

    @Test
    void paymentsSettleOldestFirst_andCanTargetOneOrder() throws Exception {
        Session s = registerWithProTenant("ar-fifo", "先欠先还店");
        long p = product(s);
        long c = customer(s, "张老板");
        long o1 = saleId(sale(s, c, p, 5, "0")); // 欠 50
        long o2 = saleId(sale(s, c, p, 3, "0")); // 欠 30
        long o3 = saleId(sale(s, c, p, 2, "0")); // 欠 20

        MockHttpServletResponse paid = pay(s, c, "65", null);
        assertThat(paid.getStatus()).as(paid.getContentAsString()).isEqualTo(201);
        assertThat((String) read(paid, "$.paymentNo")).matches("RC-\\d{8}-\\d{4}");
        assertThat((List<String>) read(paid, "$.allocations[*].amount")).containsExactly("50.00", "15.00");
        assertThat((String) read(order(s, o1), "$.paymentStatus")).isEqualTo("PAID");
        assertThat((String) read(order(s, o2), "$.paymentStatus")).isEqualTo("PARTIAL");
        assertThat((String) read(order(s, o2), "$.outstanding")).isEqualTo("15.00");
        assertThat((String) read(order(s, o3), "$.paymentStatus")).isEqualTo("UNPAID");
        assertThat(outstanding(s, c)).isEqualTo("35.00");

        MockHttpServletResponse over = pay(s, c, "35.01", null);
        assertThat((String) read(over, "$.code")).isEqualTo("PAYMENT_EXCEEDS_BALANCE");
        assertThat((String) read(over, "$.details.outstanding")).isEqualTo("35.00");

        // 指定只收第 3 张单
        assertThat((String) read(pay(s, c, "20.01", o3), "$.code")).isEqualTo("PAYMENT_EXCEEDS_BALANCE");
        assertThat(pay(s, c, "20", o3).getStatus()).isEqualTo(201);
        assertThat((String) read(order(s, o3), "$.paymentStatus")).isEqualTo("PAID");
        assertThat((String) read(order(s, o2), "$.paymentStatus")).isEqualTo("PARTIAL"); // 未受影响
        assertThat(outstanding(s, c)).isEqualTo("15.00");

        MockHttpServletResponse summary = send(get("/api/v1/receivables/summary"), s, null);
        assertThat((String) read(summary, "$.totalOutstanding")).isEqualTo("15.00");
        assertThat((Integer) read(summary, "$.customerCount")).isEqualTo(1);
        assertThat((String) read(summary, "$.collectedThisMonth")).isEqualTo("85.00");
        assertThat((List<?>) read(order(s, o2), "$.payments")).hasSize(1);
        assertInvariants(s);
    }

    @Test
    void voidingRestoresDebt_andPaidOrdersCannotBeCancelled() throws Exception {
        Session s = registerWithProTenant("ar-void", "作废店");
        long p = product(s);
        long c = customer(s, "李姐");
        long o1 = saleId(sale(s, c, p, 4, "0")); // 欠 40
        long o2 = saleId(sale(s, c, p, 2, "0")); // 欠 20
        long paymentId = ((Number) read(pay(s, c, "50", null), "$.id")).longValue();
        assertThat(outstanding(s, c)).isEqualTo("10.00");

        MockHttpServletResponse cancel = send(post("/api/v1/sales-orders/" + o1 + "/cancel"), s, "{}");
        assertThat(cancel.getStatus()).isEqualTo(409);
        assertThat((String) read(cancel, "$.code")).isEqualTo("ORDER_HAS_PAYMENTS");

        assertThat(send(post("/api/v1/customer-payments/" + paymentId + "/void"), s, "{\"reason\":\" \"}").getStatus())
                .isEqualTo(400);
        MockHttpServletResponse voided = send(post("/api/v1/customer-payments/" + paymentId + "/void"), s,
                "{\"reason\":\"记错金额\"}");
        assertThat((String) read(voided, "$.status")).isEqualTo("VOIDED");
        assertThat(outstanding(s, c)).isEqualTo("60.00");
        assertThat((String) read(order(s, o1), "$.paymentStatus")).isEqualTo("UNPAID");
        assertThat(read(order(s, o1), "$.lastPaymentAt") == null).isTrue();
        assertThat((String) read(send(post("/api/v1/customer-payments/" + paymentId + "/void"), s,
                "{\"reason\":\"再次\"}"), "$.code")).isEqualTo("PAYMENT_ALREADY_VOIDED");

        // 作废后可以取消；未收过款的赊账单取消后欠款随之消失
        assertThat(send(post("/api/v1/sales-orders/" + o1 + "/cancel"), s, "{}").getStatus()).isEqualTo(200);
        assertThat(outstanding(s, c)).isEqualTo("20.00");
        assertThat(send(post("/api/v1/sales-orders/" + o2 + "/cancel"), s, "{}").getStatus()).isEqualTo(200);
        assertThat(outstanding(s, c)).isEqualTo("0");
        assertThat((String) read(pay(s, c, "1", null), "$.code")).isEqualTo("PAYMENT_EXCEEDS_BALANCE");
        assertInvariants(s);
    }

    @Test
    void concurrentPaymentsNeverExceedDebt() throws Exception {
        Session s = registerWithProTenant("ar-cc", "并发收款店");
        long p = product(s);
        long c = customer(s, "并发客户");
        saleId(sale(s, c, p, 10, "0")); // 欠 100
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    return pay(s, c, "60", null).getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : fs) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 400);
        } finally {
            pool.shutdownNow();
        }
        assertThat(outstanding(s, c)).isEqualTo("40.00");
        assertInvariants(s);
    }

    // ---------------- 对账单与账龄 ----------------

    @Test
    void statementBalancesAndAging() throws Exception {
        Session s = registerWithProTenant("ar-stmt", "对账店");
        long p = product(s);
        long c = customer(s, "对账客户");
        long a = saleId(sale(s, c, p, 10, "30"));   // 应收 100，实收 30，欠 70
        // 销售单 A 移到 40 天前（账龄 31-60 天）
        jdbcTemplate.update("update sales_orders set order_date = date_sub(utc_timestamp(6), interval 40 day) where id = ?", a);
        assertThat(pay(s, c, "50", null).getStatus()).isEqualTo(201); // 冲抵 A：A 还欠 20
        long b = saleId(sale(s, c, p, 4, "0"));     // 欠 40
        assertThat(outstanding(s, c)).isEqualTo("60.00");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        // 期间包含全部：期初 0 + 70 + 40 − 50 = 60
        MockHttpServletResponse all = send(get("/api/v1/customers/" + c + "/statement?from=" + today.minusDays(60)
                + "&to=" + today), s, null);
        assertThat((String) read(all, "$.openingBalance")).isEqualTo("0");
        assertThat((String) read(all, "$.totalDebit")).isEqualTo("110.00");
        assertThat((String) read(all, "$.totalCredit")).isEqualTo("50.00");
        assertThat((String) read(all, "$.closingBalance")).isEqualTo("60.00");
        assertThat((String) read(all, "$.totalSales")).isEqualTo("140.00");
        assertThat((List<String>) read(all, "$.lines[*].type")).containsExactly("SALE", "PAYMENT", "SALE");
        assertThat((List<String>) read(all, "$.lines[*].balance")).containsExactly("70.00", "20.00", "60.00");

        // 最近 10 天：期初 = A 形成的欠款 70；期间收款 50、新增 40 → 期末 60
        MockHttpServletResponse recent = send(get("/api/v1/customers/" + c + "/statement?from=" + today.minusDays(10)
                + "&to=" + today), s, null);
        assertThat((String) read(recent, "$.openingBalance")).isEqualTo("70.00");
        assertThat((String) read(recent, "$.closingBalance")).isEqualTo("60.00");
        assertThat((List<?>) read(recent, "$.lines")).hasSize(2);

        // 账龄：A 剩余 20 在 31-60 天，B 40 在 0-30 天
        MockHttpServletResponse summary = send(get("/api/v1/receivables/summary"), s, null);
        assertThat((String) read(summary, "$.aging.days0To30")).isEqualTo("40.00");
        assertThat((String) read(summary, "$.aging.days31To60")).isEqualTo("20.00");
        assertThat((String) read(summary, "$.overdue30")).isEqualTo("20.00");
        assertThat((List<?>) read(send(get("/api/v1/receivables/customers?minAgeDays=30"), s, null), "$")).hasSize(1);
        assertThat((List<?>) read(send(get("/api/v1/receivables/customers?minAgeDays=60"), s, null), "$")).isEmpty();
        assertThat((Integer) read(send(get("/api/v1/receivables/customers"), s, null), "$[0].oldestDays"))
                .isBetween(39, 41);

        MockHttpServletResponse csv = send(get("/api/v1/receivables/export"), s, null);
        assertThat(csv.getStatus()).isEqualTo(200);
        assertThat(new String(csv.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)).contains("对账客户").contains("60.00");
        assertThat(b).isPositive();
        assertInvariants(s);
    }

    // ---------------- 店铺隔离 ----------------

    @Test
    void otherStoresCannotSeeOrTouchPayments() throws Exception {
        Session a = registerWithProTenant("ar-iso-a", "隔离A");
        Session b = registerWithProTenant("ar-iso-b", "隔离B");
        long p = product(a);
        long c = customer(a, "A的客户");
        long o = saleId(sale(a, c, p, 5, "0"));
        long paymentId = ((Number) read(pay(a, c, "10", null), "$.id")).longValue();

        assertThat(send(get("/api/v1/customer-payments/" + paymentId), b, null).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/v1/customer-payments/" + paymentId + "/void"), b, "{\"reason\":\"x\"}").getStatus())
                .isEqualTo(404);
        assertThat(send(get("/api/v1/customers/" + c + "/statement"), b, null).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/receivables/customers/" + c), b, null).getStatus()).isEqualTo(404);
        assertThat(pay(b, c, "1", null).getStatus()).isEqualTo(404);
        long bc = customer(b, "B的客户");
        assertThat(pay(b, bc, "1", o).getStatus()).isEqualTo(404); // 不能冲抵别家的销售单
        assertThat((String) read(send(get("/api/v1/receivables/summary"), b, null), "$.totalOutstanding")).isEqualTo("0");
        assertThat((Integer) read(send(get("/api/v1/customer-payments"), b, null), "$.totalItems")).isZero();
        assertThat(outstanding(a, c)).isEqualTo("40.00");
    }

    // ---------------- 套餐规则 ----------------

    @Test
    void upgradeConvertsRemainingStandardDays_andDowngradeIsBlocked() throws Exception {
        Session admin = admin();
        Session s = registerWithFreeTenant("ar-up", "升级店");
        Long tenantId = currentTenantId(s, mvc);
        jdbcTemplate.update("update tenants set plan = 'STANDARD', plan_expires_at = date_add(utc_timestamp(6), interval 20 day) "
                + "where id = ?", tenantId);
        // 20 × 6.99 ÷ 9.9 = 14.12 → 14（剩余时间比 20 天略少，折算结果仍为 14）
        assertThat((Integer) read(send(get("/api/v1/billing/current"), s, null), "$.proUpgradeBonusDays")).isEqualTo(14);

        String proCode = read(send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"专业版\",\"plan\":\"PRO\",\"durationDays\":30,\"trial\":false,\"quantity\":1}"), "$.codes[0]");
        MockHttpServletResponse redeemed = send(post("/api/v1/billing/redeem"), s, "{\"code\":\"%s\"}".formatted(proCode));
        assertThat((String) read(redeemed, "$.plan")).isEqualTo("PRO");
        Integer days = jdbcTemplate.queryForObject(
                "select timestampdiff(day, utc_timestamp(6), plan_expires_at) from tenants where id = ?", Integer.class, tenantId);
        assertThat(days).isBetween(43, 44); // 30 + 14
        assertThat((String) read(send(get("/api/v1/billing/history"), s, null), "$[0].note")).contains("折算").contains("14 天");
        assertThat((Boolean) read(send(get("/api/v1/billing/current"), s, null), "$.features.RECEIVABLES")).isTrue();

        // 专业版有效期内不能兑换 / 购买标准版
        String stdCode = read(send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"标准版\",\"plan\":\"STANDARD\",\"durationDays\":30,\"trial\":false,\"quantity\":1}"), "$.codes[0]");
        assertThat((String) read(send(post("/api/v1/billing/redeem"), s, "{\"code\":\"%s\"}".formatted(stdCode)), "$.code"))
                .isEqualTo("PLAN_DOWNGRADE_NOT_ALLOWED");
        List<Integer> stdMonth = read(send(get("/api/v1/billing/prices?currency=CNY"), s, null),
                "$[?(@.plan == 'STANDARD' && @.period == 'MONTH')].id");
        List<Integer> wechat = read(send(get("/api/v1/billing/payment-methods"), s, null), "$[?(@.code == 'WECHAT_PAY')].id");
        assertThat((String) read(send(post("/api/v1/billing/payment-orders"), s,
                "{\"priceId\":%d,\"paymentMethodId\":%d}".formatted(stdMonth.get(0), wechat.get(0))), "$.code"))
                .isEqualTo("PLAN_DOWNGRADE_NOT_ALLOWED");

        // 首月体验不能用于专业版
        assertThat(send(post("/api/v1/admin/code-batches"), admin,
                "{\"name\":\"x\",\"plan\":\"PRO\",\"durationDays\":30,\"trial\":true,\"quantity\":1}").getStatus()).isEqualTo(400);
    }

    @Test
    void expiredProKeepsDataReadable() throws Exception {
        Session s = registerWithProTenant("ar-exp", "专业到期店");
        long p = product(s);
        long c = customer(s, "到期客户");
        long o = saleId(sale(s, c, p, 3, "10"));
        jdbcTemplate.update("update tenants set plan_expires_at = date_sub(utc_timestamp(6), interval 1 day) where id = ?",
                currentTenantId(s, mvc));
        assertThat(send(get("/api/v1/receivables/summary"), s, null).getStatus()).isEqualTo(403);
        MockHttpServletResponse detail = order(s, o);
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat((String) read(detail, "$.outstanding")).isEqualTo("20.00");
        assertThat(new BigDecimal(jdbcTemplate.queryForObject("select paid_amount from sales_orders where id = ?",
                String.class, o))).isEqualByComparingTo("10");
    }
}
