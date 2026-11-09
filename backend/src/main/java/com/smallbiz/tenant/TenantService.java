package com.smallbiz.tenant;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.AuthService;
import com.smallbiz.auth.AuthService.IssuedSession;
import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.TenantDtos.CreateTenantRequest;
import com.smallbiz.tenant.TenantDtos.TenantView;
import com.smallbiz.tenant.TenantDtos.UpdateTenantRequest;

@Service
public class TenantService {

    /** 防止滥用：每个用户最多创建的店铺数。 */
    static final int MAX_OWNED_TENANTS = 5;

    private final TenantRepository tenants;
    private final TenantMemberRepository members;
    private final UserRepository users;
    private final AuthService authService;
    private final jakarta.persistence.EntityManager em;

    public TenantService(TenantRepository tenants, TenantMemberRepository members, UserRepository users,
            AuthService authService, jakarta.persistence.EntityManager em) {
        this.em = em;
        this.tenants = tenants;
        this.members = members;
        this.users = users;
        this.authService = authService;
    }

    /** 创建商户，创建者成为 OWNER，并返回绑定到新商户的会话。 */
    @Transactional
    public IssuedSession create(Long userId, CreateTenantRequest req, String currentRefreshToken) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "请重新登录"));
        long owned = members.findByUserIdAndStatusOrderByIdAsc(userId, TenantMember.Status.ACTIVE).stream()
                .filter(m -> m.getRole() == MemberRole.OWNER).count();
        if (owned >= MAX_OWNED_TENANTS) {
            throw ApiException.conflict("TENANT_LIMIT_REACHED", "每个账号最多创建 " + MAX_OWNED_TENANTS + " 个店铺");
        }
        Tenant newTenant = new Tenant(req.name().trim(), req.businessType(), userId);
        if (req.currency() != null && !req.currency().isBlank()) {
            String currency = Currencies.require(req.currency());
            newTenant.setCurrency(currency);
            newTenant.setTimezone(Currencies.DEFAULT_TIMEZONE.get(currency));
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            newTenant.setTimezone(validTimezone(req.timezone()));
        }
        Tenant tenant = tenants.save(newTenant);
        members.save(new TenantMember(tenant.getId(), userId, MemberRole.OWNER));
        // 每家店一个默认仓库。当前会话的店铺 id 在请求开始时就确定了（那时还没有这家店），
        // 所以用原生 SQL 在同一事务里显式写入 tenant_id
        em.createNativeQuery("""
                INSERT INTO warehouses (tenant_id, name, is_default, active, sort_order, version, created_at, updated_at)
                VALUES (:t, :n, TRUE, TRUE, 0, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """).setParameter("t", tenant.getId()).setParameter("n", com.smallbiz.warehouse.DefaultWarehouse.DEFAULT_NAME)
                .executeUpdate();
        authService.logout(currentRefreshToken);
        return authService.issue(user, tenant.getId());
    }

    @Transactional(readOnly = true)
    public TenantView get(Long tenantId) {
        Tenant t = load(tenantId);
        return TenantView.of(t, Instant.now(), currencyLocked(t));
    }

    /** 已确认过币种、且已有销售 / 采购 / 收款记录：不能再改币种（否则历史金额的含义会变）。 */
    private boolean currencyLocked(Tenant t) {
        if (!t.isCurrencyConfirmed()) {
            return false;
        }
        Number n = (Number) em.createNativeQuery("""
                SELECT (SELECT COUNT(*) FROM sales_orders WHERE tenant_id = :t)
                     + (SELECT COUNT(*) FROM purchase_orders WHERE tenant_id = :t)
                     + (SELECT COUNT(*) FROM customer_payments WHERE tenant_id = :t)
                """).setParameter("t", t.getId()).getSingleResult();
        return n.longValue() > 0;
    }

    private static String validTimezone(String tz) {
        try {
            ZoneId.of(tz);
            return tz;
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "时区不正确");
        }
    }

    @Transactional
    public TenantView update(Long tenantId, UpdateTenantRequest req) {
        try {
            ZoneId.of(req.timezone());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "时区不正确");
        }
        Tenant t = load(tenantId);
        if (req.currency() != null && !req.currency().isBlank()) {
            String currency = Currencies.require(req.currency());
            if (!currency.equals(t.getCurrency())) {
                if (currencyLocked(t)) {
                    throw ApiException.conflict("CURRENCY_LOCKED", "店铺已有销售、采购或收款记录，不能再修改币种");
                }
                t.setCurrency(currency);
            } else if (!t.isCurrencyConfirmed()) {
                t.setCurrency(currency); // 店主确认了当前币种
            }
        }
        t.setName(req.name().trim());
        t.setBusinessType(req.businessType());
        t.setTimezone(req.timezone());
        t.setContactPhone(blankToNull(req.contactPhone()));
        t.setAddress(blankToNull(req.address()));
        t.setAllowNegativeStock(req.allowNegativeStock());
        return TenantView.of(t, Instant.now(), currencyLocked(t));
    }

    private Tenant load(Long tenantId) {
        return tenants.findById(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
