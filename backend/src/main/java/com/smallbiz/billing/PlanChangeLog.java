package com.smallbiz.billing;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.smallbiz.tenant.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 套餐变更记录：每次开通、续期、手动调整都记一条，便于对账和处理纠纷。 */
@Entity
@Table(name = "plan_change_logs")
public class PlanChangeLog {

    public enum Source { ACTIVATION_CODE, MANUAL_PAYMENT, ONLINE_PAYMENT, ADMIN_MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "activation_code_id")
    private Long activationCodeId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_plan", nullable = false, length = 20)
    private Plan oldPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_plan", nullable = false, length = 20)
    private Plan newPlan;

    @Column(name = "old_expires_at")
    private Instant oldExpiresAt;

    @Column(name = "new_expires_at")
    private Instant newExpiresAt;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanChangeLog() {
    }

    public PlanChangeLog(Long tenantId, Source source, Long activationCodeId, Long paymentOrderId, Plan oldPlan,
            Plan newPlan, Instant oldExpiresAt, Instant newExpiresAt, Long operatorId, String note) {
        this.tenantId = tenantId;
        this.source = source;
        this.activationCodeId = activationCodeId;
        this.paymentOrderId = paymentOrderId;
        this.oldPlan = oldPlan;
        this.newPlan = newPlan;
        this.oldExpiresAt = oldExpiresAt;
        this.newExpiresAt = newExpiresAt;
        this.operatorId = operatorId;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public Plan getOldPlan() {
        return oldPlan;
    }

    public Plan getNewPlan() {
        return newPlan;
    }

    public Instant getOldExpiresAt() {
        return oldExpiresAt;
    }

    public Instant getNewExpiresAt() {
        return newExpiresAt;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
