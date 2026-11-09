package com.smallbiz.sales;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(Long id);

    /** 某客户未结清的销售单，按开单时间从早到晚（先欠先还），加行锁防止并发收款超收。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from SalesOrder o
            where o.customerId = :customerId and o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
              and o.paymentStatus <> com.smallbiz.sales.SalesOrder.PaymentStatus.PAID
            order by o.orderDate asc, o.id asc
            """)
    java.util.List<SalesOrder> findUnpaidByCustomerForUpdate(Long customerId);

    /** 按 id 顺序加锁（作废收款时，避免死锁）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id in :ids order by o.id asc")
    java.util.List<SalesOrder> findAllByIdForUpdate(java.util.Collection<Long> ids);

    /** 所有未结清的有效销售单（应收汇总、账龄）。 */
    @Query("""
            select o from SalesOrder o
            where o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
              and o.paymentStatus <> com.smallbiz.sales.SalesOrder.PaymentStatus.PAID
              and o.customerId is not null
            order by o.orderDate asc, o.id asc
            """)
    java.util.List<SalesOrder> findAllUnpaid();

    /** 对账单：某客户在某时间之前开的有效销售单。 */
    java.util.List<SalesOrder> findByCustomerIdAndStatusAndOrderDateLessThanOrderByOrderDateAscIdAsc(Long customerId,
            SalesOrder.Status status, java.time.Instant before);

    /** 开过单的人（开单人筛选用；含已离开店铺的员工）。 */
    @Query("select distinct o.createdBy from SalesOrder o where o.createdBy is not null")
    List<Long> findCreatorIds();

    /** [订单数, 累计消费, 最近下单时间] —— 只统计未取消的订单。 */
    @Query("""
            select count(o), coalesce(sum(o.totalAmount), 0), max(o.orderDate)
            from SalesOrder o
            where o.customerId = :customerId and o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
            """)
    List<Object[]> customerStats(Long customerId);
}
