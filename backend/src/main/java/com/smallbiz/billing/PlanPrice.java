package com.smallbiz.billing;

import java.math.BigDecimal;

import com.smallbiz.tenant.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 套餐价格（平台级，后台可改）。 */
@Entity
@Table(name = "plan_prices")
public class PlanPrice {

    /** LIFETIME：高级永久会员（一次性付款，开通对象是账号）。 */
    public enum Period { TRIAL_MONTH, MONTH, YEAR, LIFETIME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Period period;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PlanPrice() {
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public Period getPeriod() {
        return period;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isLifetime() {
        return period == Period.LIFETIME;
    }

    public boolean isTrial() {
        return period == Period.TRIAL_MONTH;
    }
}
