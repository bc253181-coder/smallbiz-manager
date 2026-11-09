package com.smallbiz.billing.membership;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.common.ApiException;

/** 普通会员限制的读取和修改（平台后台）。修改后立即生效（PlanPolicy 的快照随之更新）。 */
@Service
public class MembershipConfigService {

    private final MembershipConfigRepository repo;

    public MembershipConfigService(MembershipConfigRepository repo) {
        this.repo = repo;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void load() {
        repo.findById((short) 1).ifPresent(c -> PlanPolicy.setFreeLimits(c.toLimits()));
    }

    /** 当前限制（每次从数据库读取，多台服务器时也一致）。 */
    @Transactional(readOnly = true)
    public FreeLimits current() {
        FreeLimits l = repo.findById((short) 1).map(MembershipConfig::toLimits).orElse(FreeLimits.DEFAULT);
        PlanPolicy.setFreeLimits(l);
        return l;
    }

    @Transactional
    public FreeLimits update(FreeLimits l, Long adminId) {
        if (l.maxStores() < 1 || l.maxStores() > 5) {
            throw ApiException.badRequest("INVALID_LIMIT", "店铺上限为 1–5");
        }
        if (l.maxProducts() < 0 || l.maxProducts() > 100_000) {
            throw ApiException.badRequest("INVALID_LIMIT", "商品上限为 0–100000");
        }
        if (l.reportDays() < 1 || l.reportDays() > 3660) {
            throw ApiException.badRequest("INVALID_LIMIT", "报表天数为 1–3660");
        }
        MembershipConfig c = repo.findById((short) 1).orElseThrow();
        c.update(l.maxStores(), l.maxProducts(), l.reportDays(), l.advancedTools(), l.advancedTemplates(), l.export(),
                l.advancedAnalytics(), adminId);
        PlanPolicy.setFreeLimits(c.toLimits());
        return c.toLimits();
    }
}
