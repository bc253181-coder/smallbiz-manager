package com.smallbiz.billing.membership;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.User;
import com.smallbiz.billing.PlanChangeLog;
import com.smallbiz.billing.PlanChangeLogRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;
import com.smallbiz.tenant.TenantRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * 会员：普通会员的次数限制（按账号累计，服务端加锁计数）、开通 / 取消高级永久会员。
 *
 * <p>永久会员名下的店铺都设为「PRO、没有到期时间」，所以现有的功能检查（PlanGuard 等）不需要改。
 */
@Service
public class MembershipService {

    public static final String FREE = "FREE";
    public static final String LIFETIME = "LIFETIME";

    /** 账号的会员与用量（前端展示用）。max 为 null 表示不限。 */
    public record Usage(String membership, Instant lifetimeSince, int storesUsed, Integer storesMax, int productsUsed,
            Integer productsMax, Integer reportDays, FreeLimits freeLimits) {
    }

    private final EntityManager em;
    private final TenantRepository tenants;
    private final TenantMemberRepository members;
    private final AccountUsageRepository usage;
    private final MembershipLogRepository logs;
    private final PlanChangeLogRepository planLogs;
    private final MembershipConfigService config;

    public MembershipService(EntityManager em, TenantRepository tenants, TenantMemberRepository members,
            AccountUsageRepository usage, MembershipLogRepository logs, PlanChangeLogRepository planLogs,
            MembershipConfigService config) {
        this.em = em;
        this.tenants = tenants;
        this.members = members;
        this.usage = usage;
        this.logs = logs;
        this.planLogs = planLogs;
        this.config = config;
    }

    // ---------------- 次数限制 ----------------

    /** 新开店铺前调用：普通会员按账号累计，达到上限拒绝。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeStore(Long userId) {
        if (user(userId).isLifetime()) {
            return;
        }
        AccountUsage a = lockUsage(userId);
        int max = config.current().maxStores();
        if (a.getStoresCreated() >= max) {
            throw limitReached("stores", max, a.getStoresCreated());
        }
        a.addStores(1);
    }

    /**
     * 新建商品前调用（n 个）：店铺是普通会员时计入店主账号；员工建的商品同样计入店主。
     * 高级会员、以前的订阅有效期内不计数。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeProducts(Long tenantId, int n) {
        Long owner = limitedOwner(tenantId);
        if (owner == null || n <= 0) {
            return;
        }
        AccountUsage a = lockUsage(owner);
        int max = config.current().maxProducts();
        if (a.getProductsCreated() + n > max) {
            throw limitReached("products", max, a.getProductsCreated());
        }
        a.addProducts(n);
    }

    /** 还能新建几个商品；不限时为 null（导入前预检用）。 */
    @Transactional(readOnly = true)
    public Integer remainingProducts(Long tenantId) {
        Long owner = limitedOwner(tenantId);
        if (owner == null) {
            return null;
        }
        int used = usage.findById(owner).map(AccountUsage::getProductsCreated).orElse(0);
        return Math.max(0, config.current().maxProducts() - used);
    }

    /** 店铺是普通会员时返回店主（要计数的账号），否则 null。 */
    private Long limitedOwner(Long tenantId) {
        Tenant t = tenants.findById(tenantId).orElse(null);
        if (t == null || t.effectivePlan(Instant.now()) != Plan.FREE) {
            return null;
        }
        return ownerOf(tenantId);
    }

    @Transactional(readOnly = true)
    public Usage usage(Long userId, Long tenantId) {
        User u = user(userId);
        FreeLimits l = config.current();
        AccountUsage a = usage.findById(userId).orElse(null);
        int stores = a == null ? 0 : a.getStoresCreated();
        Long owner = tenantId == null ? userId : ownerOf(tenantId);
        int products = owner == null ? 0 : usage.findById(owner).map(AccountUsage::getProductsCreated).orElse(0);
        boolean storeFree = tenantId == null ? !u.isLifetime()
                : tenants.findById(tenantId).map(t -> t.effectivePlan(Instant.now()) == Plan.FREE).orElse(true);
        return new Usage(u.getMembership(), u.getLifetimeSince(), stores, u.isLifetime() ? null : l.maxStores(), products,
                storeFree ? l.maxProducts() : null, storeFree ? l.reportDays() : null, l);
    }

    public static ApiException limitReached(String kind, int max, int used) {
        String what = "stores".equals(kind) ? "个店铺" : "个商品";
        String msg = "已达到免费使用次数：普通会员最多创建 " + max + " " + what
                + ("products".equals(kind) ? "（删除不返还次数）" : "") + "。升级高级永久会员即可不受限制";
        return new ApiException(HttpStatus.FORBIDDEN, "PLAN_LIMIT_REACHED", msg,
                Map.of("limit", kind, "max", max, "used", used, "requiredPlan", "LIFETIME"));
    }

    // ---------------- 高级永久会员 ----------------

    public boolean isLifetime(Long userId) {
        return user(userId).isLifetime();
    }

    /** 店铺的店主（店铺的会员等级由店主决定）。 */
    public Long ownerOf(Long tenantId) {
        return members.findByTenantIdAndStatusOrderByIdAsc(tenantId, TenantMember.Status.ACTIVE).stream()
                .filter(m -> m.getRole() == MemberRole.OWNER).map(TenantMember::getUserId).findFirst().orElse(null);
    }

    /**
     * 开通高级永久会员：账号设为永久会员，名下所有店铺设为永久。账号行加锁，并发开通只成功一次。
     *
     * @param source ACTIVATION_CODE / MANUAL_PAYMENT / ONLINE_PAYMENT / ADMIN_MANUAL
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User grantLifetime(Long userId, PlanChangeLog.Source source, Long paymentOrderId, Long activationCodeId,
            Long operatorId, String note) {
        User u = em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (u == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "账号不存在");
        }
        if (u.isLifetime()) {
            throw ApiException.conflict("ALREADY_LIFETIME", "你已经是高级永久会员，无需再开通");
        }
        String old = u.getMembership();
        u.setMembership(LIFETIME, Instant.now());
        for (TenantMember m : members.findByUserIdAndStatusOrderByIdAsc(userId, TenantMember.Status.ACTIVE)) {
            if (m.getRole() == MemberRole.OWNER) {
                makeStoreLifetime(m.getTenantId(), source, activationCodeId, paymentOrderId, operatorId, note);
            }
        }
        logs.save(new MembershipLog(userId, old, LIFETIME, source.name(), paymentOrderId, activationCodeId, operatorId, note));
        return u;
    }

    /** 永久会员新开的店铺直接是永久的。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStoreCreated(Long ownerId, Long tenantId) {
        if (user(ownerId).isLifetime()) {
            makeStoreLifetime(tenantId, PlanChangeLog.Source.ADMIN_MANUAL, null, null, ownerId, "高级永久会员新开的店铺");
        }
    }

    /** 平台管理员取消永久会员（例如退款）：账号回到普通会员，名下永久的店铺回到普通会员，数据全部保留。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public User revokeLifetime(Long userId, Long adminId, String note) {
        User u = em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (u == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "账号不存在");
        }
        if (!u.isLifetime()) {
            throw ApiException.conflict("NOT_LIFETIME", "该账号不是高级永久会员");
        }
        u.setMembership(FREE, null);
        for (TenantMember m : members.findByUserIdAndStatusOrderByIdAsc(userId, TenantMember.Status.ACTIVE)) {
            if (m.getRole() != MemberRole.OWNER) {
                continue;
            }
            Tenant t = tenants.findByIdForUpdate(m.getTenantId()).orElse(null);
            if (t != null && t.isLifetime()) {
                Plan oldPlan = t.getPlan();
                t.changePlan(Plan.FREE, null);
                planLogs.save(new PlanChangeLog(t.getId(), PlanChangeLog.Source.ADMIN_MANUAL, null, null, oldPlan, Plan.FREE,
                        null, null, adminId, "取消高级永久会员：" + note));
            }
        }
        logs.save(new MembershipLog(userId, LIFETIME, FREE, PlanChangeLog.Source.ADMIN_MANUAL.name(), null, null, adminId, note));
        return u;
    }

    private void makeStoreLifetime(Long tenantId, PlanChangeLog.Source source, Long codeId, Long orderId, Long operatorId,
            String note) {
        Tenant t = tenants.findByIdForUpdate(tenantId).orElse(null);
        if (t == null || t.isLifetime()) {
            return;
        }
        Plan oldPlan = t.getPlan();
        Instant oldExpires = t.getPlanExpiresAt();
        t.makeLifetime();
        planLogs.save(new PlanChangeLog(tenantId, source, codeId, orderId, oldPlan, Plan.PRO, oldExpires, null, operatorId,
                "高级永久会员" + (note == null ? "" : "（" + note + "）")));
    }

    /**
     * 加锁读取用量行。行在注册时就建好（AuthService），这里直接对已存在的行加排他锁；
     * 不要先 INSERT IGNORE：对已存在的行它会先加共享锁，并发时两个请求都从共享锁升级排他锁会死锁。
     */
    private AccountUsage lockUsage(Long userId) {
        return usage.findForUpdate(userId).orElseGet(() -> {
            usage.ensureRow(userId); // 兜底：极少数没有用量行的旧账号
            return usage.findForUpdate(userId).orElseThrow();
        });
    }

    private User user(Long userId) {
        User u = em.find(User.class, userId);
        if (u == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "账号不存在");
        }
        return u;
    }
}
