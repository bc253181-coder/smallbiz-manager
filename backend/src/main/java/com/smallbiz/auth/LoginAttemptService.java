package com.smallbiz.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 登录失败限制：同一邮箱 15 分钟内失败 5 次后锁定 15 分钟。
 * 单机内存实现；多实例部署时需换成 Redis 等共享存储。
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant firstFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Attempts a = attempts.get(email);
        if (a == null) {
            return false;
        }
        if (a.firstFailure().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(email);
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    public void recordFailure(String email) {
        Instant now = Instant.now();
        attempts.compute(email, (k, a) -> {
            if (a == null || a.firstFailure().plus(WINDOW).isBefore(now)) {
                return new Attempts(1, now);
            }
            return new Attempts(a.count() + 1, a.firstFailure());
        });
    }

    public void reset(String email) {
        attempts.remove(email);
    }
}
