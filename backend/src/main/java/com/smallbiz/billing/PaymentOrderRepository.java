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
}
