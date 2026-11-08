package com.smallbiz.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;

import com.smallbiz.sales.SalesOrder;

/**
 * 报表查询。全部使用 JPQL，因此自动带上当前店铺条件（@TenantId），不需要手写 tenant_id。
 * 已取消的销售单、未收货的采购单一律不计入。
 */
public interface ReportRepository extends Repository<SalesOrder, Long> {

    /** [orderDate, totalAmount, totalCost]：按日/月分桶在 Java 中完成（按店铺时区）。 */
    @Query("""
            select o.orderDate, o.totalAmount, o.totalCost from SalesOrder o
            where o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
              and o.orderDate >= :from and o.orderDate < :to
            """)
    List<Object[]> salesRows(Instant from, Instant to);

    /** [receivedAt, totalAmount]：已收货的采购单，按收货时间统计。 */
    @Query("""
            select o.receivedAt, o.totalAmount from PurchaseOrder o
            where o.status = com.smallbiz.purchasing.PurchaseStatus.RECEIVED
              and o.receivedAt >= :from and o.receivedAt < :to
            """)
    List<Object[]> purchaseRows(Instant from, Instant to);

    /**
     * 商品销售排行：[productId, 商品名, SKU, 单位, 数量, 销售额, 成本]。
     * 销售额按明细小计（未分摊整单优惠）。
     */
    @Query("""
            select i.productId, max(i.productName), max(i.sku), max(i.unit), sum(i.quantity), sum(i.lineTotal),
                   sum(i.unitCost * i.quantity)
            from SalesOrderItem i join i.order o
            where o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED
              and o.orderDate >= :from and o.orderDate < :to
            group by i.productId
            """)
    List<Object[]> productRows(Instant from, Instant to);

    /** 客户排行：[customerId, 客户名, 订单数, 实收金额]（不含散客）。 */
    @Query("""
            select o.customerId, max(o.customerName), count(o), sum(o.totalAmount)
            from SalesOrder o
            where o.status = com.smallbiz.sales.SalesOrder.Status.COMPLETED and o.customerId is not null
              and o.orderDate >= :from and o.orderDate < :to
            group by o.customerId
            order by sum(o.totalAmount) desc
            """)
    List<Object[]> customerRows(Instant from, Instant to);

    /** 按分类的库存：[categoryId, 商品数, 库存数量, 按成本价值（只计正库存）]。 */
    @Query("""
            select p.categoryId, count(p), sum(p.stockQty),
                   sum(case when p.stockQty > 0 then p.stockQty * p.costPrice else 0 end)
            from Product p
            group by p.categoryId
            """)
    List<Object[]> inventoryByCategory();
}
