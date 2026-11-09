package com.smallbiz.billing.online;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 支付渠道发来的付款通知原文（排查用，90 天后自动删除）。 */
@Entity
@Table(name = "payment_notifications")
public class PaymentNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "order_no", length = 30)
    private String orderNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false, length = 200)
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentNotification() {
    }

    public PaymentNotification(String channel, String orderNo, String body, boolean verified, String result) {
        this.channel = channel;
        this.orderNo = orderNo == null || orderNo.length() <= 30 ? orderNo : orderNo.substring(0, 30);
        this.body = body.length() > 20_000 ? body.substring(0, 20_000) : body;
        this.verified = verified;
        this.result = result.length() > 200 ? result.substring(0, 200) : result;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
