package com.smallbiz.receivable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long>,
        JpaSpecificationExecutor<CustomerPayment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPayment p where p.id = :id")
    Optional<CustomerPayment> findByIdForUpdate(Long id);

    /** 某时间之后（含）收到的有效收款合计。 */
    @Query("""
            select coalesce(sum(p.amount), 0) from CustomerPayment p
            where p.status = com.smallbiz.receivable.CustomerPayment.Status.VALID and p.receivedAt >= :from
            """)
    BigDecimal sumValidSince(Instant from);

    java.util.List<CustomerPayment> findByCustomerIdAndStatusAndReceivedAtLessThanOrderByReceivedAtAscIdAsc(
            Long customerId, CustomerPayment.Status status, Instant before);
}