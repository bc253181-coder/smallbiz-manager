package com.smallbiz.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smallbiz.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("smallbiz.security.jwt-secret 必须设置且至少 32 个字符（环境变量 JWT_SECRET）");
        }
    }

    @ConfigurationProperties(prefix = "smallbiz.cors")
    public record Cors(List<String> allowedOrigins) {
    }
}
