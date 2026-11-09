package com.smallbiz.billing;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.security.TokenService;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;

/**
 * 激活码：批量生成（原文只返回一次）、兑换、作废。
 * 码格式 SBZ-XXXX-XXXX-XXXX，去掉易混淆字符（0 O 1 I），约 2^76 种组合；数据库只存 SHA-256。
 */
@Service
public class ActivationService {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int MAX_ATTEMPTS_PER_HOUR = 10;

    /** lifetime：高级永久会员激活码（忽略套餐和天数）。 */
    public record BatchRequest(String name, Plan plan, int durationDays, boolean trial, boolean lifetime, int quantity,
            Instant expiresAt) {
    }

    public record CreatedBatch(ActivationCodeBatch batch, List<String> codes) {
    }

    private final ActivationCodeRepository codes;
    private final ActivationCodeBatchRepository batches;
    private final PlanService plans;
    private final com.smallbiz.billing.membership.MembershipService membership;
    private final com.smallbiz.tenant.TenantRepository tenants;
    private final CodeCrypto crypto;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, List<Instant>> attempts = new ConcurrentHashMap<>();

    public ActivationService(ActivationCodeRepository codes, ActivationCodeBatchRepository batches, PlanService plans,
            com.smallbiz.billing.membership.MembershipService membership, com.smallbiz.tenant.TenantRepository tenants,
            CodeCrypto crypto) {
        this.crypto = crypto;
        this.membership = membership;
        this.tenants = tenants;
        this.codes = codes;
        this.batches = batches;
        this.plans = plans;
    }

    @Transactional
    public CreatedBatch createBatch(BatchRequest req, Long adminId) {
        if (req.lifetime()) {
            req = new BatchRequest(req.name(), Plan.PRO, 0, false, true, req.quantity(), req.expiresAt());
        } else if (req.plan() == Plan.FREE) {
            throw ApiException.badRequest("INVALID_PLAN", "激活码只能用于付费套餐");
        }
        if (req.trial() && req.plan() != Plan.STANDARD) {
            throw ApiException.badRequest("INVALID_PLAN", "首月体验只适用于标准版");
        }
        if (req.quantity() < 1 || req.quantity() > 1000) {
            throw ApiException.badRequest("INVALID_QUANTITY", "每批数量为 1–1000");
        }
        if (!req.lifetime() && (req.durationDays() < 1 || req.durationDays() > 3660)) {
            throw ApiException.badRequest("INVALID_DURATION", "天数为 1–3660");
        }
        String name = req.name() == null || req.name().isBlank() ? "未命名批次" : req.name().trim();
        ActivationCodeBatch batch = batches.save(new ActivationCodeBatch(name, req.plan(), req.durationDays(),
                req.trial(), req.lifetime(), req.quantity(), req.expiresAt(), adminId));
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < req.quantity(); i++) {
            String code = generate();
            raw.add(code);
            // 配置了密钥时原文加密保存（发码台用）；兑换只比对哈希
            codes.save(new ActivationCode(batch.getId(), TokenService.sha256(normalize(code)), code.substring(code.length() - 4),
                    crypto.available() ? crypto.encrypt(code) : null));
        }
        return new CreatedBatch(batch, raw);
    }

    /** 兑换激活码，返回开通后的店铺。 */
    @Transactional
    public Tenant redeem(Long tenantId, Long userId, String rawCode) {
        throttle(tenantId);
        String normalized = normalize(rawCode);
        ActivationCode code = codes.findByHashForUpdate(TokenService.sha256(normalized))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID", "激活码不正确，请检查后重新输入"));
        if (code.getStatus() == ActivationCode.Status.REDEEMED) {
            throw ApiException.conflict("CODE_USED", "该激活码已被使用");
        }
        if (code.getStatus() == ActivationCode.Status.VOIDED) {
            throw ApiException.conflict("CODE_VOIDED", "该激活码已作废");
        }
        ActivationCodeBatch batch = batches.findById(code.getBatchId()).orElseThrow();
        Instant now = Instant.now();
        if (batch.getExpiresAt() != null && batch.getExpiresAt().isBefore(now)) {
            throw ApiException.conflict("CODE_EXPIRED", "该激活码已过兑换期限");
        }
        Tenant t;
        if (batch.isLifetime()) {
            // 永久会员码：开通对象是兑换的账号（名下所有店铺）
            membership.grantLifetime(userId, PlanChangeLog.Source.ACTIVATION_CODE, null, code.getId(), userId,
                    "激活码 ****" + code.getCodeHint());
            t = tenants.findById(tenantId).orElseThrow();
        } else {
            t = plans.grant(tenantId, batch.getPlan(), batch.getDurationDays(), batch.isTrial(),
                    PlanChangeLog.Source.ACTIVATION_CODE, code.getId(), null, userId, "激活码 ****" + code.getCodeHint());
        }
        code.redeem(tenantId, userId, now);
        attempts.remove(tenantId);
        return t;
    }

    /** 作废未使用的激活码（例如小红书订单退款）。 */
    @Transactional
    public ActivationCode voidCode(String rawCode) {
        ActivationCode code = codes.findByHashForUpdate(TokenService.sha256(normalize(rawCode)))
                .orElseThrow(() -> ApiException.notFound("CODE_INVALID", "激活码不存在"));
        if (code.getStatus() != ActivationCode.Status.UNUSED) {
            throw ApiException.conflict("CODE_NOT_UNUSED", "只有未使用的激活码可以作废");
        }
        code.voidCode();
        return code;
    }

    /** 统一格式：大写、去掉空格和横线后按 SBZ-XXXX-XXXX-XXXX 重组。 */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.startsWith("SBZ")) {
            s = s.substring(3);
        }
        if (s.length() != 12) {
            return "SBZ-" + s;
        }
        return "SBZ-" + s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8, 12);
    }

    private String generate() {
        StringBuilder sb = new StringBuilder("SBZ");
        for (int i = 0; i < 12; i++) {
            if (i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 防暴力猜码：每个店铺每小时最多尝试 10 次（单机内存实现）。 */
    private void throttle(Long tenantId) {
        Instant now = Instant.now();
        List<Instant> list = attempts.computeIfAbsent(tenantId, k -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(t -> t.isBefore(now.minus(Duration.ofHours(1))));
            if (list.size() >= MAX_ATTEMPTS_PER_HOUR) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "尝试次数过多，请 1 小时后再试");
            }
            list.add(now);
        }
    }
}
