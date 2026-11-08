package com.smallbiz.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    /** 某商品所有流水的变动合计（用于一致性校验：应等于当前库存）。 */
    @Query("select coalesce(sum(m.qtyChange), 0) from StockMovement m where m.productId = :productId")
    long sumChangeByProductId(Long productId);
}
