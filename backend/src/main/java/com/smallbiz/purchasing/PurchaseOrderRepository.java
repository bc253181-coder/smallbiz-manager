package com.smallbiz.purchasing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>,
        JpaSpecificationExecutor<PurchaseOrder> {

    /** 加行锁：状态变更（收货/取消/修改）时串行执行，防止重复收货。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(Long id);

    /**
     * 某供应商供应过的商品（仅已收货的采购单）：
     * [productId, 商品名, SKU, 单位, 累计收货数量, 最近收货时间, 采购次数]
     */
    @Query("""
            select i.productId, max(i.productName), max(i.sku), max(i.unit), sum(i.receivedQty),
                   max(o.receivedAt), count(distinct o.id)
            from PurchaseOrderItem i join i.order o
            where o.supplierId = :supplierId and o.status = com.smallbiz.purchasing.PurchaseStatus.RECEIVED
            group by i.productId
            order by max(o.receivedAt) desc
            """)
    List<Object[]> suppliedProducts(Long supplierId);
}
