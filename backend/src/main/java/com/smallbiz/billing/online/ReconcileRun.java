package com.smallbiz.billing.online;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 一次对账：查询了多少张未付款的在线付款单，补开通了多少张。 */
@Entity
@Table(name = "payment_reconcile_runs")
public class ReconcileRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int checked;

    @Column(nullable = false)
    private int fixed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconcileRun() {
    }

    public ReconcileRun(int checked, int fixed) {
        this.checked = checked;
        this.fixed = fixed;
        this.createdAt = Instant.now();
    }

    public int getChecked() {
        return checked;
    }

    public int getFixed() {
        return fixed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
