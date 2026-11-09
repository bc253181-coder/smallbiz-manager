package com.smallbiz.billing.online;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.smallbiz.billing.PaymentOrder;
import com.smallbiz.billing.PaymentService;
import com.smallbiz.billing.PaymentService.PaidResult;
import com.smallbiz.common.ApiException;

/**
 * 在线支付：创建付款页、处理付款通知、定时对账。
 * 数据库操作都在 {@link PaymentService} 的事务里完成；调用支付渠道（网络请求）不在事务中进行。
 */
@Service
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);
    static final Duration NOTIFICATION_RETENTION = Duration.ofDays(90);

    public record Channel(String channel, String displayName) {
    }

    public record Started(PaymentOrder order, String checkoutUrl) {
    }

    private final Map<String, PaymentGateway> gateways;
    private final PaymentService payments;
    private final PaymentNotificationRepository notifications;
    private final ReconcileRunRepository runs;

    public OnlinePaymentService(ObjectProvider<PaymentGateway> gateways, PaymentService payments,
            PaymentNotificationRepository notifications, ReconcileRunRepository runs) {
        // 没有任何渠道时（正式环境还没接入真实渠道）为空：在线支付按钮不显示
        this.gateways = gateways.orderedStream().collect(Collectors.toMap(PaymentGateway::channel, Function.identity()));
        this.payments = payments;
        this.notifications = notifications;
        this.runs = runs;
    }

    public List<Channel> channels(String currency) {
        return gateways.values().stream().filter(g -> g.supports(currency))
                .map(g -> new Channel(g.channel(), g.displayName())).sorted(java.util.Comparator.comparing(Channel::channel))
                .toList();
    }

    public Started start(Long tenantId, Long userId, Long priceId, String channel) {
        PaymentGateway g = gateway(channel);
        PaymentOrder o = payments.createOnline(tenantId, userId, priceId, g.channel());
        if (!g.supports(o.getCurrency())) {
            payments.cancelSilently(o.getId());
            throw ApiException.badRequest("CHANNEL_CURRENCY", g.displayName() + " 不支持 " + o.getCurrency());
        }
        String url;
        try {
            url = g.createCheckout(o);
        } catch (RuntimeException e) {
            log.warn("创建付款页失败 {} {}：{}", channel, o.getOrderNo(), e.toString());
            payments.cancelSilently(o.getId());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_ERROR", "支付渠道暂时不可用，请稍后再试或使用扫码付款");
        }
        payments.setCheckout(o.getId(), url);
        o.setCheckoutRef(url);
        return new Started(o, url);
    }

    /**
     * 处理渠道发来的付款通知。签名不对返回 false（接口回 400，渠道会重试或报警）；其余情况都记录并返回 true。
     */
    public boolean handleNotification(String channel, Map<String, String> headers, String body) {
        PaymentGateway g = gateways.get(channel);
        if (g == null) {
            throw ApiException.notFound("CHANNEL_NOT_FOUND", "支付渠道不存在");
        }
        Optional<PaymentGateway.Paid> paid;
        try {
            paid = g.verify(headers, body);
        } catch (PaymentGateway.InvalidNotificationException | IllegalArgumentException e) {
            notifications.save(new PaymentNotification(channel, null, body, false, "签名校验失败：" + e.getMessage()));
            log.warn("付款通知签名校验失败（{}）", channel);
            return false;
        } catch (RuntimeException e) {
            notifications.save(new PaymentNotification(channel, null, body, false, "无法解析：" + e.getMessage()));
            return false;
        }
        if (paid.isEmpty()) {
            notifications.save(new PaymentNotification(channel, null, body, true, "IGNORED"));
            return true;
        }
        PaidResult r = payments.applyOnlinePayment(channel, paid.get());
        notifications.save(new PaymentNotification(channel, paid.get().orderNo(), body, true, r.name()));
        if (r == PaidResult.ANOMALY || r == PaidResult.UNKNOWN_ORDER) {
            log.warn("在线付款需要人工处理：{} {} {}", channel, paid.get().orderNo(), r);
        }
        return true;
    }

    /** 每 10 分钟：向渠道查询还没收到付款通知的订单，付了款的补开通；顺便清理 90 天前的通知记录。 */
    @Scheduled(initialDelayString = "${smallbiz.payment.reconcile-initial-delay:PT1M}",
            fixedDelayString = "${smallbiz.payment.reconcile-interval:PT10M}")
    public ReconcileRun reconcile() {
        int checked = 0;
        int fixed = 0;
        for (PaymentOrder o : payments.onlineUnpaid()) {
            PaymentGateway g = gateways.get(o.getChannel());
            if (g == null) {
                continue;
            }
            checked++;
            try {
                Optional<PaymentGateway.Paid> paid = g.query(o);
                if (paid.isPresent()) {
                    PaidResult r = payments.applyOnlinePayment(g.channel(), paid.get());
                    notifications.save(new PaymentNotification(g.channel(), o.getOrderNo(), "{\"source\":\"reconcile\"}",
                            true, "RECONCILE " + r.name()));
                    if (r == PaidResult.GRANTED || r == PaidResult.GRANTED_WITH_NOTE) {
                        fixed++;
                    }
                }
            } catch (RuntimeException e) {
                log.warn("对账查询失败 {} {}：{}", o.getChannel(), o.getOrderNo(), e.toString());
            }
        }
        notifications.deleteOlderThan(Instant.now().minus(NOTIFICATION_RETENTION));
        if (fixed > 0) {
            log.info("对账补开通 {} 张付款单", fixed);
        }
        return runs.save(new ReconcileRun(checked, fixed));
    }

    public Optional<ReconcileRun> lastRun() {
        return runs.findTopByOrderByIdDesc();
    }

    public Optional<PaymentGateway> find(String channel) {
        return Optional.ofNullable(gateways.get(channel));
    }

    private PaymentGateway gateway(String channel) {
        PaymentGateway g = channel == null ? null : gateways.get(channel);
        if (g == null) {
            throw ApiException.badRequest("CHANNEL_NOT_AVAILABLE", "该支付方式暂不可用");
        }
        return g;
    }
}
