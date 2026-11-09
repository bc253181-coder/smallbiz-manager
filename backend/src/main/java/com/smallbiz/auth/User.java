package com.smallbiz.auth;

import java.time.Instant;

import com.smallbiz.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** 登录账号（全局，不属于某个商户）。 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public enum Status { ACTIVE, DISABLED }

    @Column(nullable = false, length = 191, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** 界面语言：zh-CN / en；为空表示按浏览器语言。 */
    @Column(length = 10)
    private String locale;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin = false;

    /** 会员等级（跟着账号）：FREE 普通会员 / LIFETIME 高级永久会员。 */
    @Column(nullable = false, length = 20)
    private String membership = "FREE";

    @Column(name = "lifetime_since")
    private java.time.Instant lifetimeSince;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public void setPlatformAdmin(boolean platformAdmin) {
        this.platformAdmin = platformAdmin;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public void acceptTerms(String version, Instant at) {
        this.termsVersion = version;
        this.termsAcceptedAt = at;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public boolean isLifetime() {
        return "LIFETIME".equals(membership);
    }

    public String getMembership() {
        return membership;
    }

    public java.time.Instant getLifetimeSince() {
        return lifetimeSince;
    }

    public void setMembership(String membership, java.time.Instant since) {
        this.membership = membership;
        this.lifetimeSince = since;
    }
}
