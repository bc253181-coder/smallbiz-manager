package com.smallbiz.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * 每个商户独立的递增序号（商品编号、销售单号、采购单号……）。
 * 使用 upsert 加行锁，保证同一商户内并发取号不重复。
 * 注意：这里是原生 SQL，必须显式传入 tenantId。
 */
@Service
public class SequenceService {

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

    private final EntityManager em;
    private final TenantRepository tenants;

    public SequenceService(EntityManager em, TenantRepository tenants) {
        this.em = em;
        this.tenants = tenants;
    }

    /**
     * 单据号：前缀-店铺当地日期-当日序号，例如 PO-20261108-0001。
     * 每个店铺、每个前缀、每天独立从 1 开始。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String documentNo(Long tenantId, String prefix) {
        java.time.ZoneId zone = tenants.findById(tenantId).map(Tenant::getTimezone).map(java.time.ZoneId::of)
                .orElse(java.time.ZoneId.of("Asia/Shanghai"));
        String day = java.time.LocalDate.now(zone).format(DAY);
        int n = next(tenantId, prefix + "-" + day);
        return String.format("%s-%s-%04d", prefix, day, n);
    }

    /** 返回下一个序号（从 1 开始）。需要在调用方事务内执行，事务提交前该行保持锁定。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public int next(Long tenantId, String key) {
        em.createNativeQuery("""
                INSERT INTO tenant_sequences (tenant_id, seq_key, next_value) VALUES (:tid, :key, 1)
                ON DUPLICATE KEY UPDATE next_value = next_value + 1
                """)
                .setParameter("tid", tenantId)
                .setParameter("key", key)
                .executeUpdate();
        Number value = (Number) em.createNativeQuery(
                "SELECT next_value FROM tenant_sequences WHERE tenant_id = :tid AND seq_key = :key")
                .setParameter("tid", tenantId)
                .setParameter("key", key)
                .getSingleResult();
        return value.intValue();
    }
}
