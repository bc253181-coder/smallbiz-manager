package com.smallbiz.tenant;

import java.time.Instant;

import com.smallbiz.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 商户（店铺/公司）。 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 30)
    private BusinessType businessType;

    @Column(nullable = false, length = 3)
    private String currency = "CNY";

    @Column(nullable = false, length = 50)
    private String timezone = "Asia/Shanghai";

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(name = "plan_expires_at")
    private Instant planExpiresAt;

    @Column(name = "trial_used", nullable = false)
    private boolean trialUsed = false;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 300)
    private String address;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private int version;

    protected Tenant() {
    }

    public Tenant(String name, BusinessType businessType, Long createdBy) {
        this.name = name;
        this.businessType = businessType;
        this.createdBy = createdBy;
    }

    /** 当前实际生效的套餐：付费套餐过期后视为免费版（数据保留）。 */
    public Plan effectivePlan(Instant now) {
        if (plan == Plan.FREE) {
            return Plan.FREE;
        }
        return (planExpiresAt != null && planExpiresAt.isAfter(now)) ? plan : Plan.FREE;
    }

    /** 开通/续期/调整套餐。FREE 时到期时间为空。 */
    public void changePlan(Plan plan, Instant expiresAt) {
        this.plan = plan;
        this.planExpiresAt = plan == Plan.FREE ? null : expiresAt;
    }

    public void markTrialUsed() {
        this.trialUsed = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isAllowNegativeStock() {
        return allowNegativeStock;
    }

    public void setAllowNegativeStock(boolean allowNegativeStock) {
        this.allowNegativeStock = allowNegativeStock;
    }

    public Plan getPlan() {
        return plan;
    }

    public Instant getPlanExpiresAt() {
        return planExpiresAt;
    }

    public boolean isTrialUsed() {
        return trialUsed;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
