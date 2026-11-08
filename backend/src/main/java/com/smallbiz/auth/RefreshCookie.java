package com.smallbiz.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.smallbiz.security.SecurityProperties;

/** 刷新令牌 Cookie：HttpOnly（前端 JS 读不到）、SameSite=Lax、仅发送到 /api/v1/auth。 */
@Component
public class RefreshCookie {

    public static final String NAME = "sb_refresh";
    private static final String PATH = "/api/v1/auth";

    private final SecurityProperties props;

    public RefreshCookie(SecurityProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String token) {
        return build(token, props.refreshTokenTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
