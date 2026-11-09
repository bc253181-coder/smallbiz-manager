package com.smallbiz.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(Long id);

    List<PaymentOrder> findTop50ByTenantIdOrderByIdDesc(Long tenantId);

    List<PaymentOrder> findByTenantIdAndStatusIn(Long tenantId, List<PaymentOrder.Status> statuses);

    List<PaymentOrder> findTop200ByStatusOrderBySubmittedAtAsc(PaymentOrder.Status status);

    List<PaymentOrder> findTop200ByOrderByIdDesc();

    boolean existsByReferenceCode(String referenceCode);

    long countByStatus(PaymentOrder.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderNo = :orderNo")
    Optional<PaymentOrder> findByOrderNoForUpdate(String orderNo);

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    /** 对账：最近创建、还没有收到付款的在线支付付款单。 */
    @Query("select o from PaymentOrder o where o.channel is not null and o.providerTxnId is null "
            + "and o.status in :statuses and o.createdAt >= :since order by o.id")
    List<PaymentOrder> findOnlineUnpaidSince(List<PaymentOrder.Status> statuses, java.time.Instant since);

    List<PaymentOrder> findTop200ByAnomalyIsNotNullAndAnomalyResolvedAtIsNullOrderByIdDesc();

    long countByAnomalyIsNotNullAndAnomalyResolvedAtIsNull();
}