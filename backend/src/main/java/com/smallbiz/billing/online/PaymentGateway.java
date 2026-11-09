package com.smallbiz.billing.online;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.smallbiz.billing.PaymentOrder;

/**
 * 在线支付渠道。每个渠道（模拟支付、以后的 Stripe / 微信支付等）实现这个接口：
 * 创建付款页、校验付款通知的签名、查询订单状态（对账）。
 *
 * <p>开通只依据 {@link #verify} 校验通过的通知或 {@link #query} 的查询结果，从不相信浏览器带回来的参数。
 */
public interface PaymentGateway {

    /** 渠道代码，保存在付款单上，例如 MOCK。 */
    String channel();

    /** 显示给用户的名称。 */
    String displayName();

    boolean supports(String currency);

    /** 创建付款页，返回用户要跳转去的地址。 */
    String createCheckout(PaymentOrder order);

    /**
     * 校验并解析付款通知。签名不对时抛出 {@link InvalidNotificationException}；
     * 通知不是「付款成功」时返回 empty（例如付款失败、退款通知，本阶段不处理）。
     */
    Optional<Paid> verify(Map<String, String> headers, String body);

    /** 向渠道查询订单是否已付款（付款通知丢失时用于对账）。 */
    Optional<Paid> query(PaymentOrder order);

    /** 渠道确认的一笔付款。 */
    record Paid(String orderNo, String txnId, BigDecimal amount, String currency, Instant paidAt) {
    }

    class InvalidNotificationException extends RuntimeException {
        public InvalidNotificationException(String message) {
            super(message);
        }
    }
}
