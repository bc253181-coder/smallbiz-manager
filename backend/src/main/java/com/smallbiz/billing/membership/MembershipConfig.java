package com.smallbiz.billing.membership;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 普通会员的限制（只有一行，id = 1；平台后台可修改）。 */
@Entity
@Table(name = "membership_config")
public class MembershipConfig {

    @Id
    private Short id;

    @Column(name = "free_max_stores", nullable = false)
    private int freeMaxStores;

    @Column(name = "free_max_products", nullable = false)
    private int freeMaxProducts;

    @Column(name = "free_report_days", nullable = false)
    private int freeReportDays;

    @Column(name = "free_advanced_tools", nullable = false)
    private boolean freeAdvancedTools;

    @Column(name = "free_advanced_templates", nullable = false)
    private boolean freeAdvancedTemplates;

    @Column(name = "free_export", nullable = false)
    private boolean freeExport;

    @Column(name = "free_advanced_analytics", nullable = false)
    private boolean freeAdvancedAnalytics;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MembershipConfig() {
    }

    public void update(int maxStores, int maxProducts, int reportDays, boolean tools, boolean templates, boolean export,
            boolean analytics, Long adminId) {
        this.freeMaxStores = maxStores;
        this.freeMaxProducts = maxProducts;
        this.freeReportDays = reportDays;
        this.freeAdvancedTools = tools;
        this.freeAdvancedTemplates = templates;
        this.freeExport = export;
        this.freeAdvancedAnalytics = analytics;
        this.updatedBy = adminId;
        this.updatedAt = Instant.now();
    }

    public FreeLimits toLimits() {
        return new FreeLimits(freeMaxStores, freeMaxProducts, freeReportDays, freeAdvancedTools, freeAdvancedTemplates,
                freeExport, freeAdvancedAnalytics);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
