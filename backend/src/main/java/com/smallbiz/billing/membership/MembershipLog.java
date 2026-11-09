package com.smallbiz.billing.membership;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 会员等级变更记录（按账号）。source：ACTIVATION_CODE / MANUAL_PAYMENT / ONLINE_PAYMENT / ADMIN_MANUAL。 */
@Entity
@Table(name = "membership_logs")
public class MembershipLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "old_membership", nullable = false, length = 20)
    private String oldMembership;

    @Column(name = "new_membership", nullable = false, length = 20)
    private String newMembership;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "activation_code_id")
    private Long activationCodeId;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MembershipLog() {
    }

    public MembershipLog(Long userId, String oldMembership, String newMembership, String source, Long paymentOrderId,
            Long activationCodeId, Long operatorId, String note) {
        this.userId = userId;
        this.oldMembership = oldMembership;
        this.newMembership = newMembership;
        this.source = source;
        this.paymentOrderId = paymentOrderId;
        this.activationCodeId = activationCodeId;
        this.operatorId = operatorId;
        this.note = note == null || note.length() <= 200 ? note : note.substring(0, 200);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOldMembership() {
        return oldMembership;
    }

    public String getNewMembership() {
        return newMembership;
    }

    public String getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
