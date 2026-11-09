package com.smallbiz.billing.online;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付渠道的付款通知：无需登录，但必须通过签名校验（在各渠道的 verify 中完成）。 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class PaymentWebhookController {

    private final OnlinePaymentService online;

    public PaymentWebhookController(OnlinePaymentService online) {
        this.online = online;
    }

    @PostMapping("/{channel}")
    public ResponseEntity<String> notify(@PathVariable String channel, @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String body) {
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
        boolean ok = online.handleNotification(channel.toUpperCase(Locale.ROOT), lower, body == null ? "" : body);
        return ok ? ResponseEntity.ok("OK") : ResponseEntity.badRequest().body("INVALID");
    }
}
