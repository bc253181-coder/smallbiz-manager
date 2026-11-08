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

    private final EntityManager em;

    public SequenceService(EntityManager em) {
        this.em = em;
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
