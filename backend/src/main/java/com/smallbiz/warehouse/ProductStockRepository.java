package com.smallbiz.warehouse;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    List<ProductStock> findByProductId(Long productId);

    List<ProductStock> findByWarehouseIdAndProductIdIn(Long warehouseId, Collection<Long> productIds);

    /** [仓库 id, 有库存的商品种数, 库存总量, 库存价值（按商品当前成本）]；不含已删除商品。 */
    @Query("""
            select s.warehouseId, sum(case when s.qty <> 0 then 1 else 0 end), coalesce(sum(s.qty), 0),
                   coalesce(sum(s.qty * p.costPrice), 0)
            from ProductStock s join com.smallbiz.catalog.Product p on p.id = s.productId
            group by s.warehouseId
            """)
    List<Object[]> warehouseTotals();

    /** 仓库里还有库存（含已删除商品的库存，以免停用后库存"消失"）。 */
    @Query("select count(s) > 0 from ProductStock s where s.warehouseId = :warehouseId and s.qty <> 0")
    boolean hasStock(Long warehouseId);
}
