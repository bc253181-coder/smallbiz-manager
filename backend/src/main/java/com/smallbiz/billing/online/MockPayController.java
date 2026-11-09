package com.smallbiz.billing.online;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.billing.PaymentOrder;
import com.smallbiz.billing.PaymentOrderRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.Plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 模拟支付页面用的接口（只在开发和测试环境存在）。模拟「用户在渠道付款」：
 * SUCCESS = 付款并送达通知；LOST = 付款但通知丢失（等对账补开通）；FAIL = 付款失败。
 */
@RestController
@RequestMapping("/api/v1/payments/mock")
@Profile("!prod")
@ConditionalOnProperty(name = "smallbiz.payment.mock-enabled", havingValue = "true")
@PreAuthorize("hasRole('OWNER')")
public class MockPayController {

    public enum Outcome { SUCCESS, LOST, FAIL }

    public record Summary(Long orderId, String orderNo, Plan plan, int durationDays, String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, PaymentOrder.Status status, Instant expiresAt) {
    }

    public record PayRequest(@NotNull Outcome outcome) {
    }

    private final PaymentOrderRepository orders;
    private final MockPaymentGateway gateway;
    private final OnlinePaymentService online;

    public MockPayController(PaymentOrderRepository orders, MockPaymentGateway gateway, OnlinePaymentService online) {
        this.orders = orders;
        this.gateway = gateway;
        this.online = online;
    }

    @GetMapping("/{orderNo}")
    public Summary summary(@PathVariable String orderNo) {
        PaymentOrder o = load(orderNo);
        return new Summary(o.getId(), o.getOrderNo(), o.getPlan(), o.getDurationDays(), o.getCurrency(), o.getAmount(),
                o.getStatus(), o.getExpiresAt());
    }

    @PostMapping("/{orderNo}/pay")
    public Summary pay(@PathVariable String orderNo, @Valid @RequestBody PayRequest req) {
        PaymentOrder o = load(orderNo);
        if (req.outcome() != Outcome.FAIL) {
            MockPaymentGateway.Notification n = gateway.simulatePayment(o.getOrderNo(), o.getAmount(), o.getCurrency());
            if (req.outcome() == Outcome.SUCCESS) {
                online.handleNotification(MockPaymentGateway.CHANNEL, n.headers(), n.body());
            }
        }
        return summary(orderNo);
    }

    /** 只能操作自己店铺的付款单。 */
    private PaymentOrder load(String orderNo) {
        Long tenantId = CurrentUser.requireTenantId();
        return orders.findByOrderNo(orderNo)
                .filter(o -> o.getTenantId().equals(tenantId) && MockPaymentGateway.CHANNEL.equals(o.getChannel()))
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "付款单不存在"));
    }
}
