package com.smallbiz.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台运营配置。
 *
 * @param adminEmails     平台管理员邮箱（逗号分隔）；这些账号登录/注册时自动获得平台管理员身份
 * @param supportWechatId 人工客服微信号（只显示在本网站内）
 * @param paymentAssetsDir 首次启动时导入收款码图片的目录
 */
@ConfigurationProperties(prefix = "smallbiz.platform")
public record PlatformProperties(List<String> adminEmails, String supportWechatId, String paymentAssetsDir) {

    public PlatformProperties {
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty()).toList();
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.toLowerCase(Locale.ROOT));
    }
}
