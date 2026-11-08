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

    /** [订单数, 累计消费, 最近下单时间] —— 只统计未取消的订单。 */
    @Query("""
            select count(o), coalesce(sum(o.totalAmount), 0), max(o.orderDate)
            from SalesOrder o
            where o.customerId = :customerId and o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
            """)
    List<Object[]> customerStats(Long customerId);
}
