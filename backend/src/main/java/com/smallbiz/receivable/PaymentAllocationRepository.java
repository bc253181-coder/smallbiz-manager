package com.smallbiz.receivable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentIdOrderByIdAsc(Long paymentId);

    List<PaymentAllocation> findByPaymentIdIn(Collection<Long> paymentIds);

    List<PaymentAllocation> findBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    /** 该销售单是否有有效（未作废）收款冲抵。 */
    @Query("""
            select count(a) > 0 from PaymentAllocation a, CustomerPayment p
            where a.paymentId = p.id and a.salesOrderId = :orderId
              and p.status = com.smallbiz.receivable.CustomerPayment.Status.VALID
            """)
    boolean existsValidForOrder(Long orderId);

    /** 该销售单最近一次有效收款的时间。 */
    @Query("""
            select max(p.receivedAt) from PaymentAllocation a, CustomerPayment p
            where a.paymentId = p.id and a.salesOrderId = :orderId
              and p.status = com.smallbiz.receivable.CustomerPayment.Status.VALID
            """)
    Instant lastValidReceivedAt(Long orderId);
}