package com.smallbiz.auth;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.AuthDtos.ChangePasswordRequest;
import com.smallbiz.auth.AuthDtos.CurrentTenantView;
import com.smallbiz.auth.AuthDtos.RegisterRequest;
import com.smallbiz.auth.AuthDtos.SessionResponse;
import com.smallbiz.auth.AuthDtos.TenantSummary;
import com.smallbiz.auth.AuthDtos.UpdateProfileRequest;
import com.smallbiz.auth.AuthDtos.UserView;
import com.smallbiz.common.ApiException;
import com.smallbiz.security.SecurityProperties;
import com.smallbiz.security.TokenService;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;
import com.smallbiz.tenant.TenantRepository;

@Service
public class AuthService {

    /** 会话签发结果：响应体 + 需要写入 Cookie 的刷新令牌原文。 */
    public record IssuedSession(SessionResponse body, String refreshToken) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TenantRepository tenants;
    private final TenantMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SecurityProperties props;
    private final LoginAttemptService loginAttempts;
    private final com.smallbiz.config.PlatformProperties platform;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, TenantRepository tenants,
            TenantMemberRepository members, PasswordEncoder passwordEncoder, TokenService tokenService,
            SecurityProperties props, LoginAttemptService loginAttempts,
            com.smallbiz.config.PlatformProperties platform) {
        this.platform = platform;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tenants = tenants;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.props = props;
        this.loginAttempts = loginAttempts;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public IssuedSession register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "该邮箱已注册，请直接登录");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password()), req.displayName().trim()));
        user.setLastLoginAt(Instant.now());
        syncPlatformAdmin(user);
        return issue(user, null);
    }

    @Transactional
    public IssuedSession login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        if (loginAttempts.isLocked(email)) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "登录失败次数过多，请 15 分钟后再试");
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttempts.recordFailure(email);
            throw ApiException.unauthorized("BAD_CREDENTIALS", "邮箱或密码不正确");
        }
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已停用");
        }
        loginAttempts.reset(email);
        user.setLastLoginAt(Instant.now());
        syncPlatformAdmin(user);

        List<TenantMember> memberships = activeMemberships(user.getId());
        Long tenantId = memberships.size() == 1 ? memberships.get(0).getTenantId() : null;
        return issue(user, tenantId);
    }

    /**
     * 用刷新令牌换新的访问令牌，同时轮换刷新令牌。
     *
     * <p>轮换宽限期：旧令牌被轮换后的短时间内仍可再用。原因是浏览器可能在收到新 Cookie 前中断请求
     * （刷新页面、网络抖动、多个标签页同时刷新），此时浏览器手里只有旧令牌；没有宽限期用户会被意外登出。
     * 退出登录和修改密码作废的令牌不享受宽限期。
     */
    @Transactional
    public IssuedSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("REFRESH_INVALID", "登录已过期，请重新登录");
        }
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(TokenService.sha256(rawRefreshToken))
                .filter(t -> t.isUsable(now) || t.isInRotationGrace(now, props.refreshReuseGrace()))
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "登录已过期，请重新登录"));
        if (token.isUsable(now)) {
            token.rotate(now);
        }
        User user = users.findById(token.getUserId())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "登录已过期，请重新登录"));
        Long tenantId = token.getCurrentTenantId();
        if (tenantId != null && members.findByTenantIdAndUserIdAndStatus(tenantId, user.getId(),
                TenantMember.Status.ACTIVE).isEmpty()) {
            tenantId = null; // 已被移出该商户
        }
        // 平台管理员名单改了配置后，在下次刷新令牌时生效（无需重新登录）
        syncPlatformAdmin(user);
        return issue(user, tenantId);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHashForUpdate(TokenService.sha256(rawRefreshToken))
                .ifPresent(t -> t.revoke(Instant.now()));
    }

    /** 切换到用户所属的另一个商户；不是成员时返回 404（不暴露商户是否存在）。 */
    @Transactional
    public IssuedSession switchTenant(Long userId, Long tenantId, String currentRefreshToken) {
        members.findByTenantIdAndUserIdAndStatus(tenantId, userId, TenantMember.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        logout(currentRefreshToken);
        return issue(loadUser(userId), tenantId);
    }

    @Transactional(readOnly = true)
    public SessionResponse me(Long userId, Long tenantId) {
        User user = loadUser(userId);
        return buildResponse(user, tenantId, null);
    }

    @Transactional
    public UserView updateProfile(Long userId, UpdateProfileRequest req) {
        User user = loadUser(userId);
        user.setDisplayName(req.displayName().trim());
        user.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        return UserView.of(user);
    }

    /** 修改密码后，所有设备上的登录（刷新令牌）全部失效。 */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_OLD_PASSWORD", "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    /** 签发新的访问令牌与刷新令牌（调用方需在事务中）。 */
    public IssuedSession issue(User user, Long tenantId) {
        String raw = tokenService.newRefreshToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenService.sha256(raw), tenantId,
                Instant.now().plus(props.refreshTokenTtl())));
        return new IssuedSession(buildResponse(user, tenantId, raw), raw);
    }

    private SessionResponse buildResponse(User user, Long tenantId, String refreshTokenOrNull) {
        List<TenantMember> memberships = activeMemberships(user.getId());
        List<TenantSummary> summaries = memberships.stream()
                .map(m -> new TenantSummary(m.getTenantId(),
                        tenants.findById(m.getTenantId()).map(Tenant::getName).orElse(""), m.getRole()))
                .toList();

        CurrentTenantView current = null;
        TenantMember currentMember = tenantId == null ? null
                : memberships.stream().filter(m -> m.getTenantId().equals(tenantId)).findFirst().orElse(null);
        if (currentMember != null) {
            Tenant t = tenants.findById(tenantId).orElseThrow();
            current = new CurrentTenantView(t.getId(), t.getName(), t.getBusinessType(), currentMember.getRole(),
                    t.effectivePlan(Instant.now()), t.getPlanExpiresAt(), t.getCurrency(), t.getTimezone());
        }

        String accessToken = null;
        long expiresIn = 0;
        if (refreshTokenOrNull != null) {
            accessToken = tokenService.createAccessToken(user.getId(),
                    currentMember == null ? null : tenantId,
                    currentMember == null ? null : currentMember.getRole(),
                    user.isPlatformAdmin());
            expiresIn = tokenService.accessTokenTtlSeconds();
        }
        return new SessionResponse(accessToken, expiresIn, UserView.of(user), current, summaries);
    }

    /** 配置中的平台管理员邮箱自动获得平台管理员身份；从配置移除后下次登录即取消。 */
    private void syncPlatformAdmin(User user) {
        boolean shouldBe = platform.isAdminEmail(user.getEmail());
        if (user.isPlatformAdmin() != shouldBe) {
            user.setPlatformAdmin(shouldBe);
        }
    }

    private List<TenantMember> activeMemberships(Long userId) {
        return members.findByUserIdAndStatusOrderByIdAsc(userId, TenantMember.Status.ACTIVE);
    }

    private User loadUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "请重新登录"));
    }
}
