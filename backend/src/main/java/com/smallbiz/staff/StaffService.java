package com.smallbiz.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.AuthService;
import com.smallbiz.auth.AuthService.IssuedSession;
import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.common.ApiException;
import com.smallbiz.security.TokenService;
import com.smallbiz.staff.StaffDtos.CreateInvitationRequest;
import com.smallbiz.staff.StaffDtos.CreatedInvitation;
import com.smallbiz.staff.StaffDtos.InvitationPreview;
import com.smallbiz.staff.StaffDtos.InvitationView;
import com.smallbiz.staff.StaffDtos.MemberView;
import com.smallbiz.staff.StaffDtos.MembersResponse;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;
import com.smallbiz.tenant.TenantRepository;

/**
 * 员工邀请与成员管理。名额 = 在职员工 + 未过期的待接受邀请；邀请和接受都锁店铺行，防止并发超额。
 */
@Service
public class StaffService {

    static final Duration INVITATION_TTL = Duration.ofDays(7);
    static final int MAX_PREVIEWS_PER_MINUTE = 30;

    private final InvitationRepository invitations;
    private final TenantMemberRepository members;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final TokenService tokenService;
    private final AuthService authService;
    private final Map<String, Deque<Instant>> previewRate = new ConcurrentHashMap<>();

    public StaffService(InvitationRepository invitations, TenantMemberRepository members, TenantRepository tenants,
            UserRepository users, TokenService tokenService, AuthService authService) {
        this.invitations = invitations;
        this.members = members;
        this.tenants = tenants;
        this.users = users;
        this.tokenService = tokenService;
        this.authService = authService;
    }

    // ---------------- 店主：成员与邀请 ----------------

    @Transactional(readOnly = true)
    public MembersResponse list(Long tenantId, Long selfUserId) {
        Instant now = Instant.now();
        Tenant t = loadTenant(tenantId);
        List<TenantMember> active = members.findByTenantIdAndStatusOrderByIdAsc(tenantId, TenantMember.Status.ACTIVE);
        Map<Long, User> byId = users.findAllById(active.stream().map(TenantMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberView> views = active.stream()
                // 店主排在最前，其余按加入顺序
                .sorted((a, b) -> Boolean.compare(b.getRole() == MemberRole.OWNER, a.getRole() == MemberRole.OWNER))
                .map(m -> {
                    User u = byId.get(m.getUserId());
                    return new MemberView(m.getId(), m.getUserId(), u == null ? "" : u.getDisplayName(),
                            u == null ? "" : u.getEmail(), m.getRole(), m.getJoinedAt(),
                            u == null ? null : u.getLastLoginAt(), m.getUserId().equals(selfUserId));
                })
                .toList();
        List<Invitation> pending = invitations.findPending(tenantId, now);
        Plan plan = t.effectivePlan(now);
        int used = (int) active.stream().filter(m -> m.getRole() != MemberRole.OWNER).count() + pending.size();
        return new MembersResponse(views, pending.stream().map(InvitationView::of).toList(), used,
                PlanPolicy.maxStaff(plan), PlanPolicy.allows(plan, Feature.STAFF_ACCOUNTS));
    }

    @Transactional
    public CreatedInvitation invite(Long tenantId, Long ownerId, CreateInvitationRequest req) {
        requireAssignable(req.role());
        Instant now = Instant.now();
        Tenant t = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        Plan plan = t.effectivePlan(now);
        if (!PlanPolicy.allows(plan, Feature.STAFF_ACCOUNTS)) {
            throw PlanGuard.featureRequired(Feature.STAFF_ACCOUNTS);
        }
        int max = PlanPolicy.maxStaff(plan);
        long used = members.countStaff(tenantId) + invitations.findPending(tenantId, now).size();
        if (used >= max) {
            throw limitReached(max);
        }
        String raw = tokenService.newRefreshToken();
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        Invitation inv = invitations.save(new Invitation(tenantId, TokenService.sha256(raw), req.role(), note,
                now.plus(INVITATION_TTL), ownerId));
        return new CreatedInvitation(InvitationView.of(inv), raw);
    }

    @Transactional
    public void revoke(Long tenantId, Long invitationId) {
        Invitation inv = invitations.findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> ApiException.notFound("INVITATION_NOT_FOUND", "邀请不存在"));
        if (inv.getStatus() != Invitation.Status.PENDING) {
            throw ApiException.conflict("INVITATION_NOT_PENDING", "这个邀请已被接受或已撤销");
        }
        inv.revoke();
    }

    @Transactional
    public MemberView changeRole(Long tenantId, Long selfUserId, Long memberId, MemberRole role) {
        requireAssignable(role);
        TenantMember m = loadManageable(tenantId, memberId);
        m.setRole(role);
        User u = users.findById(m.getUserId()).orElse(null);
        return new MemberView(m.getId(), m.getUserId(), u == null ? "" : u.getDisplayName(),
                u == null ? "" : u.getEmail(), m.getRole(), m.getJoinedAt(), u == null ? null : u.getLastLoginAt(),
                m.getUserId().equals(selfUserId));
    }

    @Transactional
    public void remove(Long tenantId, Long ownerId, Long memberId) {
        loadManageable(tenantId, memberId).remove(ownerId, Instant.now());
    }

    // ---------------- 被邀请人 ----------------

    /** 公开接口：显示邀请信息。无效（不存在/过期/已用/已撤销）统一返回 INVITATION_INVALID。 */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawToken, String clientIp) {
        checkPreviewRate(clientIp);
        Instant now = Instant.now();
        Invitation inv = findUsable(rawToken == null ? null : invitations.findByTokenHash(TokenService.sha256(rawToken))
                .orElse(null), now);
        Tenant t = loadTenant(inv.getTenantId());
        String inviter = inv.getCreatedBy() == null ? "" : users.findById(inv.getCreatedBy()).map(User::getDisplayName).orElse("");
        return new InvitationPreview(t.getName(), inv.getRole(), inviter, inv.getExpiresAt());
    }

    /** 接受邀请：加入店铺并切换过去，返回新会话。 */
    @Transactional
    public IssuedSession accept(Long userId, String rawToken, String currentRefreshToken) {
        Instant now = Instant.now();
        Invitation inv = findUsable(invitations.findByTokenHashForUpdate(TokenService.sha256(rawToken)).orElse(null), now);
        Tenant t = tenants.findByIdForUpdate(inv.getTenantId())
                .orElseThrow(() -> invalid());
        Plan plan = t.effectivePlan(now);
        if (!PlanPolicy.allows(plan, Feature.STAFF_ACCOUNTS)) {
            throw AuthService.storePlanExpired();
        }
        TenantMember existing = members.findByTenantIdAndUserId(t.getId(), userId).orElse(null);
        if (existing != null && existing.getStatus() == TenantMember.Status.ACTIVE) {
            throw ApiException.conflict("ALREADY_MEMBER", "你已经是这家店铺的成员");
        }
        // 这个邀请本身已占一个名额；这里再确认在职员工没有超额（例如店主中途降低了名额）
        int max = PlanPolicy.maxStaff(plan);
        if (members.countStaff(t.getId()) >= max) {
            throw limitReached(max);
        }
        if (existing != null) {
            existing.rejoin(inv.getRole(), now);
        } else {
            members.save(new TenantMember(t.getId(), userId, inv.getRole()));
        }
        inv.accept(userId, now);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "请重新登录"));
        authService.logout(currentRefreshToken);
        return authService.issue(user, t.getId());
    }

    /** 员工自己退出店铺（店主不能退出），返回未选择店铺的新会话。 */
    @Transactional
    public IssuedSession leave(Long userId, Long tenantId, String currentRefreshToken) {
        TenantMember m = members.findByTenantIdAndUserIdAndStatus(tenantId, userId, TenantMember.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        if (m.getRole() == MemberRole.OWNER) {
            throw ApiException.conflict("OWNER_CANNOT_LEAVE", "店主不能退出自己的店铺");
        }
        m.remove(userId, Instant.now());
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "请重新登录"));
        authService.logout(currentRefreshToken);
        return authService.issue(user, null);
    }

    // ---------------- 内部 ----------------

    private TenantMember loadManageable(Long tenantId, Long memberId) {
        TenantMember m = members.findById(memberId)
                .filter(x -> x.getTenantId().equals(tenantId) && x.getStatus() == TenantMember.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "成员不存在"));
        if (m.getRole() == MemberRole.OWNER) {
            throw ApiException.conflict("CANNOT_MODIFY_OWNER", "不能修改或移除店主");
        }
        return m;
    }

    private static void requireAssignable(MemberRole role) {
        if (role == MemberRole.OWNER) {
            throw ApiException.badRequest("INVALID_ROLE", "只能设置为管理员或店员");
        }
    }

    private static Invitation findUsable(Invitation inv, Instant now) {
        if (inv == null || !inv.isUsable(now)) {
            throw invalid();
        }
        return inv;
    }

    private static ApiException invalid() {
        return ApiException.notFound("INVITATION_INVALID", "邀请链接无效或已过期，请联系店主重新发送");
    }

    private static ApiException limitReached(int max) {
        return new ApiException(HttpStatus.CONFLICT, "MEMBER_LIMIT_REACHED",
                "员工名额已满（最多 " + max + " 人，待接受的邀请也占名额）。可撤销未使用的邀请或移除员工",
                Map.of("max", max));
    }

    private Tenant loadTenant(Long tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> invalid());
    }

    private void checkPreviewRate(String ip) {
        Instant now = Instant.now();
        Deque<Instant> q = previewRate.computeIfAbsent(ip == null ? "" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_PREVIEWS_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "操作太频繁了，请稍后再试");
            }
            q.addLast(now);
        }
    }
}
