package com.smallbiz.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import com.smallbiz.billing.online.MockPayController;
import com.smallbiz.billing.online.MockPaymentGateway;
import com.smallbiz.billing.online.OnlinePaymentService;
import com.smallbiz.billing.online.PaymentGateway.Paid;
import com.smallbiz.billing.online.ReconcileRun;
import com.smallbiz.support.IntegrationTest;

/** 在线支付（模拟支付渠道，购买高级永久会员）：开通、签名、金额、重复与并发、对账、过期后付款、重复购买、隔离与后台。 */
class OnlinePaymentApiTest extends IntegrationTest {

    @Autowired
    MockPaymentGateway gateway;

    @Autowired
    OnlinePaymentService online;

    @Autowired
    JdbcTemplate jdbc;

    private record Order(long id, String orderNo, String checkoutUrl, String amount) {
    }

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

    private long priceId(Session s, String currency, String plan, String period) throws Exception {
        List<Integer> ids = read(send(get("/api/v1/billing/prices?currency=" + currency), s, null),
                "$[?(@.plan == '%s' && @.period == '%s')].id".formatted(plan, period));
        return ids.get(0);
    }

    private MockHttpServletResponse startRaw(Session s, long priceId) throws Exception {
        return send(post("/api/v1/billing/online-payments"), s, "{\"priceId\":%d,\"channel\":\"MOCK\"}".formatted(priceId));
    }

    private Order start(Session s, long priceId) throws Exception {
        MockHttpServletResponse res = startRaw(s, priceId);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return new Order(((Number) read(res, "$.order.id")).longValue(), read(res, "$.order.orderNo"),
                read(res, "$.checkoutUrl"), read(res, "$.order.amount"));
    }

    private MockHttpServletResponse mockPay(Session s, Order o, String outcome) throws Exception {
        return send(post("/api/v1/payments/mock/" + o.orderNo() + "/pay"), s, "{\"outcome\":\"%s\"}".formatted(outcome));
    }

    private MockHttpServletResponse webhook(MockPaymentGateway.Notification n) throws Exception {
        var b = post("/api/v1/payments/webhook/MOCK").contentType(MediaType.APPLICATION_JSON).content(n.body());
        n.headers().forEach(b::header);
        return mvc.perform(b).andReturn().getResponse();
    }

    private String status(Session s, Order o) throws Exception {
        return read(send(get("/api/v1/billing/payment-orders/" + o.id()), s, null), "$.status");
    }

    private String plan(Session s) throws Exception {
        return read(send(get("/api/v1/billing/current"), s, null), "$.plan");
    }

    private Instant expires(Session s) throws Exception {
        String e = read(send(get("/api/v1/billing/current"), s, null), "$.expiresAt");
        return e == null ? null : Instant.parse(e);
    }

    private int grants(Order o) {
        return jdbc.queryForObject("select count(*) from plan_change_logs where payment_order_id = ?", Integer.class, o.id());
    }

    private String anomaly(Order o) {
        return jdbc.queryForObject("select anomaly from payment_orders where id = ?", String.class, o.id());
    }

    @Test
    void payOnline_isActivatedAutomatically() throws Exception {
        Session owner = registerWithFreeTenant("online-ok", "在线店");
        List<String> channels = read(send(get("/api/v1/billing/online-channels?currency=CNY"), owner, null), "$[*].channel");
        assertThat(channels).containsExactly("MOCK");

        Order o = start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));
        assertThat(o.amount()).isEqualTo("29.90"); // 金额来自价格表
        assertThat(o.checkoutUrl()).isEqualTo("/pay/mock/" + o.orderNo());
        assertThat(status(owner, o)).isEqualTo("AWAITING_PAYMENT");
        assertThat(plan(owner)).isEqualTo("FREE");

        // 浏览器跳回来时不会开通任何东西；只有渠道的签名通知才会
        MockHttpServletResponse paid = mockPay(owner, o, "SUCCESS");
        assertThat(paid.getStatus()).isEqualTo(200);
        assertThat(status(owner, o)).isEqualTo("APPROVED");
        assertThat(plan(owner)).isEqualTo("PRO");
        assertThat(expires(owner)).isNull(); // 永久有效
        assertThat((Boolean) read(send(get("/api/v1/billing/current"), owner, null), "$.lifetime")).isTrue();

        MockHttpServletResponse view = send(get("/api/v1/billing/payment-orders/" + o.id()), owner, null);
        assertThat((String) read(view, "$.channel")).isEqualTo("MOCK");
        assertThat((String) read(view, "$.paidAt")).isNotNull();
        assertThat((String) read(view, "$.providerTxnId")).startsWith("MOCK-");
        assertThat((Object) read(view, "$.checkoutUrl")).isNull();
        assertThat((Object) read(view, "$.anomaly")).isNull();

        MockHttpServletResponse history = send(get("/api/v1/billing/history"), owner, null);
        assertThat((String) read(history, "$[0].source")).isEqualTo("ONLINE_PAYMENT");
        assertThat(jdbc.queryForObject("select result from payment_notifications where order_no = ? order by id desc limit 1",
                String.class, o.orderNo())).isEqualTo("GRANTED");

        // 已开通的付款单不能再取消
        assertThat(send(post("/api/v1/billing/payment-orders/" + o.id() + "/cancel"), owner, null).getStatus()).isEqualTo(409);
    }

    @Test
    void forgedOrTamperedNotificationsDoNotActivate() throws Exception {
        Session owner = registerWithFreeTenant("online-forge", "伪造店");
        Order o = start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));

        // 签名不对：拒绝，不开通
        MockPaymentGateway.Notification real = gateway.notification(
                new Paid(o.orderNo(), "MOCK-FAKE1", new BigDecimal(o.amount()), "CNY", Instant.now()));
        MockPaymentGateway.Notification forged = new MockPaymentGateway.Notification(real.body(),
                java.util.Map.of("x-mock-signature", "00".repeat(32)));
        assertThat(webhook(forged).getStatus()).isEqualTo(400);
        MockPaymentGateway.Notification unsigned = new MockPaymentGateway.Notification(real.body(), java.util.Map.of());
        assertThat(webhook(unsigned).getStatus()).isEqualTo(400);
        // 签名对，但正文被改过（金额改成 0.01）
        MockPaymentGateway.Notification tampered = new MockPaymentGateway.Notification(
                real.body().replace(o.amount(), "0.01"), real.headers());
        assertThat(webhook(tampered).getStatus()).isEqualTo(400);
        assertThat(status(owner, o)).isEqualTo("AWAITING_PAYMENT");
        assertThat(plan(owner)).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("select count(*) from payment_notifications where verified = false and body like ?",
                Integer.class, "%" + o.orderNo() + "%")).isEqualTo(3);

        // 签名正确但金额不符（渠道确认的付款金额和订单不一致）：不开通，记为异常
        MockHttpServletResponse res = webhook(gateway.notification(
                new Paid(o.orderNo(), "MOCK-LOW1", new BigDecimal("0.01"), "CNY", Instant.now())));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(plan(owner)).isEqualTo("FREE");
        assertThat(anomaly(o)).startsWith("金额不符：收到 CNY 0.01，应付 CNY 29.90");
        // 币种不符同样不开通
        Order o2 = startAfterCancel(owner, o);
        webhook(gateway.notification(new Paid(o2.orderNo(), "MOCK-CUR1", new BigDecimal(o2.amount()), "MYR", Instant.now())));
        assertThat(plan(owner)).isEqualTo("FREE");
        assertThat(anomaly(o2)).contains("金额不符");

        // 不存在的渠道
        assertThat(mvc.perform(post("/api/v1/payments/webhook/NOPE").content("{}")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    /** 金额不符的付款单仍是待付款状态：取消后重新下单。 */
    private Order startAfterCancel(Session owner, Order o) throws Exception {
        send(post("/api/v1/billing/payment-orders/" + o.id() + "/cancel"), owner, null);
        return start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));
    }

    @Test
    void duplicateAndConcurrentNotificationsActivateOnce() throws Exception {
        Session owner = registerWithFreeTenant("online-dup", "重复店");
        Order o = start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));
        MockPaymentGateway.Notification n = gateway.simulatePayment(o.orderNo(), new BigDecimal(o.amount()), "CNY");

        // 8 条相同通知 + 对账同时到达
        ExecutorService pool = Executors.newFixedThreadPool(9);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<Integer> task = () -> {
                go.await();
                return webhook(n).getStatus();
            };
            results.add(pool.submit(task));
        }
        results.add(pool.submit(() -> {
            go.await();
            online.reconcile();
            return 200;
        }));
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(grants(o)).isEqualTo(1);
        assertThat(expires(owner)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from membership_logs where payment_order_id = ?", Integer.class, o.id()))
                .isEqualTo(1);
        assertThat(webhook(n).getStatus()).isEqualTo(200); // 之后再收到也只是记录为重复
        assertThat(grants(o)).isEqualTo(1);

        // 同一张付款单又收到另一笔不同的付款：不再加天数，提示退款
        webhook(gateway.notification(new Paid(o.orderNo(), "MOCK-SECOND", new BigDecimal(o.amount()), "CNY", Instant.now())));
        assertThat(grants(o)).isEqualTo(1);
        assertThat(anomaly(o)).contains("重复付款").contains("MOCK-SECOND");
    }

    @Test
    void lostNotificationIsFixedByReconcile() throws Exception {
        Session owner = registerWithFreeTenant("online-lost", "对账店");
        Order o = start(owner, priceId(owner, "MYR", "PRO", "LIFETIME"));
        assertThat(o.amount()).isEqualTo("19.90");
        mockPay(owner, o, "LOST"); // 用户付了款，但通知没送到
        assertThat(status(owner, o)).isEqualTo("AWAITING_PAYMENT");
        assertThat(plan(owner)).isEqualTo("FREE");

        ReconcileRun run = online.reconcile();
        assertThat(run.getFixed()).isGreaterThanOrEqualTo(1);
        assertThat(status(owner, o)).isEqualTo("APPROVED");
        assertThat(plan(owner)).isEqualTo("PRO");
        assertThat(online.reconcile().getFixed()).isZero(); // 再次对账不会重复开通
        assertThat(grants(o)).isEqualTo(1);

        // 付款失败：什么都不变，可以取消后重新付款
        Session other = registerWithFreeTenant("online-fail", "失败店");
        Order f = start(other, priceId(other, "CNY", "PRO", "LIFETIME"));
        mockPay(other, f, "FAIL");
        online.reconcile();
        assertThat(status(other, f)).isEqualTo("AWAITING_PAYMENT");
        assertThat(plan(other)).isEqualTo("FREE");
    }

    @Test
    void paymentAfterExpiryOrCancelIsStillActivatedAndFlagged() throws Exception {
        Session owner = registerWithFreeTenant("online-late", "晚付店");
        Order o = start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));
        jdbc.update("update payment_orders set expires_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), o.id());
        assertThat(status(owner, o)).isEqualTo("EXPIRED");
        mockPay(owner, o, "SUCCESS");
        assertThat(status(owner, o)).isEqualTo("APPROVED");
        assertThat(plan(owner)).isEqualTo("PRO");
        assertThat(anomaly(o)).isEqualTo("付款单过期后才付款，已照常开通");

        // 已经是永久会员：不能再下单
        assertThat((String) read(startRaw(owner, priceId(owner, "CNY", "PRO", "LIFETIME")), "$.code"))
                .isEqualTo("ALREADY_LIFETIME");
    }

    @Test
    void lifetimePaidTwiceIsOnlyActivatedOnce() throws Exception {
        Session owner = registerWithFreeTenant("online-twice", "两单店");
        long lifetime = priceId(owner, "CNY", "PRO", "LIFETIME");
        Order a = start(owner, lifetime);
        assertThat(send(post("/api/v1/billing/payment-orders/" + a.id() + "/cancel"), owner, null).getStatus()).isEqualTo(200);
        Order b = start(owner, lifetime);
        mockPay(owner, b, "SUCCESS");
        assertThat(plan(owner)).isEqualTo("PRO");
        // 用户之后又在旧付款页付了款：不重复开通，提示退款
        mockPay(owner, a, "SUCCESS");
        assertThat(grants(a)).isZero();
        assertThat(anomaly(a)).contains("已经是高级永久会员");
        assertThat(jdbc.queryForObject("select count(*) from membership_logs where user_id = ?", Integer.class, owner.userId()))
                .isEqualTo(1);
    }
    @Test
    void ordersAreIsolatedAndOwnerOnly_andDowngradeIsRejected() throws Exception {
        Session a = registerWithFreeTenant("online-iso-a", "隔离A");
        Session b = registerWithFreeTenant("online-iso-b", "隔离B");
        Order o = start(a, priceId(a, "CNY", "PRO", "LIFETIME"));
        assertThat(send(get("/api/v1/payments/mock/" + o.orderNo()), b, null).getStatus()).isEqualTo(404);
        assertThat(mockPay(b, o, "SUCCESS").getStatus()).isEqualTo(404);
        assertThat(send(get("/api/v1/billing/payment-orders/" + o.id()), b, null).getStatus()).isEqualTo(404);
        assertThat(plan(a)).isEqualTo("FREE");

        // 有未完成的付款单时不能再下一单
        assertThat((String) read(startRaw(a, priceId(a, "CNY", "PRO", "LIFETIME")), "$.code")).isEqualTo("PAYMENT_IN_PROGRESS");
        // 未登录不能下单
        assertThat(mvc.perform(post("/api/v1/billing/online-payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\":1,\"channel\":\"MOCK\"}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        // 渠道不存在
        assertThat((String) read(send(post("/api/v1/billing/online-payments"), b,
                "{\"priceId\":%d,\"channel\":\"NOPE\"}".formatted(priceId(b, "CNY", "PRO", "LIFETIME"))), "$.code"))
                .isEqualTo("CHANNEL_NOT_AVAILABLE");

        // 以前的专业版订阅用户可以升级为永久会员
        Session pro = registerWithProTenant("online-pro", "专业店");
        assertThat(startRaw(pro, priceId(pro, "CNY", "PRO", "LIFETIME")).getStatus()).isEqualTo(201);
    }

    @Test
    void adminSeesAnomaliesAndReconcileStatus() throws Exception {
        Session owner = registerWithFreeTenant("online-admin", "异常店");
        Order o = start(owner, priceId(owner, "CNY", "PRO", "LIFETIME"));
        webhook(gateway.notification(new Paid(o.orderNo(), "MOCK-ADM1", new BigDecimal("1.00"), "CNY", Instant.now())));

        Session admin = admin();
        List<Integer> ids = read(send(get("/api/v1/admin/payment-orders?anomaly=true"), admin, null), "$[*].id");
        assertThat(ids).contains((int) o.id());
        List<Integer> all = read(send(get("/api/v1/admin/payment-orders"), admin, null), "$[*].id");
        assertThat(all).contains((int) o.id());

        assertThat(send(post("/api/v1/admin/payment-orders/" + o.id() + "/resolve-anomaly"), admin, null).getStatus()).isEqualTo(200);
        ids = read(send(get("/api/v1/admin/payment-orders?anomaly=true"), admin, null), "$[*].id");
        assertThat(ids).doesNotContain((int) o.id());

        MockHttpServletResponse st = send(post("/api/v1/admin/online-payments/reconcile"), admin, null);
        assertThat(st.getStatus()).isEqualTo(200);
        assertThat((String) read(st, "$.lastReconcileAt")).isNotNull();
        assertThat((List<?>) read(st, "$.channels")).isNotEmpty();

        assertThat(send(get("/api/v1/admin/online-payments/status"), owner, null).getStatus()).isEqualTo(403);
    }

    @Test
    void mockChannelCanNeverRunInProduction() {
        assertThat(MockPaymentGateway.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
        assertThat(MockPayController.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
    }
}
