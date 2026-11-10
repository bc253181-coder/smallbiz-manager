package com.smallbiz.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.smallbiz.billing.CodeDispenserService.Dispensed;
import com.smallbiz.common.ApiException;

/**
 * 快速发码页（docs/18）：输入密码即取出一个高级永久会员激活码，不需要登录和订单号。
 * 密码只在服务器环境变量 QUICK_CODE_PASSWORD；不配置时页面关闭。
 * 同一 IP 15 分钟内输错 5 次后锁定 15 分钟（单机内存实现）。
 */
@Service
public class QuickCodeService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Failures(int count, Instant first) {
    }

    private final byte[] password;
    private final CodeDispenserService dispenser;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public QuickCodeService(@Value("${smallbiz.codes.quick-password:}") String password, CodeDispenserService dispenser) {
        this.password = password == null || password.isBlank() ? null : password.getBytes(StandardCharsets.UTF_8);
        this.dispenser = dispenser;
    }

    public boolean enabled() {
        return password != null;
    }

    public Dispensed issue(String input, String clientIp) {
        if (!enabled()) {
            throw ApiException.notFound("QUICK_CODE_DISABLED", "快速发码没有开启");
        }
        String ip = clientIp == null ? "?" : clientIp;
        if (locked(ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "密码错误次数太多，请 15 分钟后再试");
        }
        byte[] given = (input == null ? "" : input).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, password)) {
            recordFailure(ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WRONG_PASSWORD", "密码不正确");
        }
        failures.remove(ip);
        return dispenser.quickIssue();
    }

    private boolean locked(String ip) {
        Failures f = failures.get(ip);
        if (f == null) {
            return false;
        }
        if (f.first().plus(WINDOW).isBefore(Instant.now())) {
            failures.remove(ip);
            return false;
        }
        return f.count() >= MAX_FAILURES;
    }

    private void recordFailure(String ip) {
        Instant now = Instant.now();
        failures.compute(ip, (k, f) -> f == null || f.first().plus(WINDOW).isBefore(now) ? new Failures(1, now)
                : new Failures(f.count() + 1, f.first()));
    }
}
