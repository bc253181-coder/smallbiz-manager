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

@Entity
@Table(name = "activation_code_batches")
public class ActivationCodeBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "is_trial", nullable = false)
    private boolean trial;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivationCodeBatch() {
    }

    public ActivationCodeBatch(String name, Plan plan, int durationDays, boolean trial, int quantity,
            Instant expiresAt, Long createdBy) {
        this.name = name;
        this.plan = plan;
        this.durationDays = durationDays;
        this.trial = trial;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public boolean isTrial() {
        return trial;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
