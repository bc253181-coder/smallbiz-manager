package com.smallbiz.billing.online;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.smallbiz.billing.PaymentOrder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 模拟支付：只在开发和测试环境启用（prod 环境不会创建这个组件，配置也打不开）。
 * 用和真实渠道一样的方式工作：付款通知带 HMAC-SHA256 签名，走同一个通知处理流程。
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "smallbiz.payment.mock-enabled", havingValue = "true")
public class MockPaymentGateway implements PaymentGateway {

    public static final String CHANNEL = "MOCK";
    static final String SIGNATURE_HEADER = "x-mock-signature";

    private final byte[] secret = new byte[32];
    private final ObjectMapper json = new ObjectMapper();
    /** 模拟渠道这一侧已付款的订单（用于对账查询）。 */
    private final Map<String, Paid> paid = new ConcurrentHashMap<>();

    public MockPaymentGateway() {
        new SecureRandom().nextBytes(secret);
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String displayName() {
        return "模拟支付（测试）";
    }

    @Override
    public boolean supports(String currency) {
        return true;
    }

    @Override
    public String createCheckout(PaymentOrder order) {
        return "/pay/mock/" + order.getOrderNo();
    }

    @Override
    public Optional<Paid> verify(Map<String, String> headers, String body) {
        String sig = headers.get(SIGNATURE_HEADER);
        if (sig == null || !MessageDigest.isEqual(sig.getBytes(StandardCharsets.US_ASCII),
                sign(body).getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidNotificationException("签名不正确");
        }
        JsonNode n = json.readTree(body);
        if (!"paid".equals(n.path("event").asString())) {
            return Optional.empty();
        }
        return Optional.of(new Paid(n.path("orderNo").asString(), n.path("txnId").asString(),
                new BigDecimal(n.path("amount").asString()), n.path("currency").asString(),
                Instant.parse(n.path("paidAt").asString())));
    }

    @Override
    public Optional<Paid> query(PaymentOrder order) {
        return Optional.ofNullable(paid.get(order.getOrderNo()));
    }

    /** 模拟用户在渠道付款：渠道记下这笔付款，并生成带签名的付款通知（是否送达由调用方决定）。 */
    public Notification simulatePayment(String orderNo, BigDecimal amount, String currency) {
        Paid p = new Paid(orderNo, "MOCK-" + HexFormat.of().formatHex(randomBytes(8)), amount, currency, Instant.now());
        paid.put(orderNo, p);
        return notification(p);
    }

    /** 生成一条付款通知（测试中也用来构造篡改过的通知）。 */
    public Notification notification(Paid p) {
        String body = json.writeValueAsString(Map.of("event", "paid", "orderNo", p.orderNo(), "txnId", p.txnId(),
                "amount", p.amount().toPlainString(), "currency", p.currency(), "paidAt", p.paidAt().toString()));
        return new Notification(body, Map.of(SIGNATURE_HEADER, sign(body)));
    }

    public record Notification(String body, Map<String, String> headers) {
    }

    String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
