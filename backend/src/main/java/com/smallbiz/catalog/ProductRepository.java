package com.smallbiz.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

    boolean existsByCategoryId(Long categoryId);

    /** [categoryId, count] */
    @Query("select p.categoryId, count(p) from Product p where p.categoryId is not null group by p.categoryId")
    List<Object[]> countByCategory();

    /** 原生 SQL（绕过软删除过滤），因此显式带 tenant_id。 */
    @Modifying
    @Query(value = "update products set category_id = null "
            + "where tenant_id = :tenantId and category_id = :categoryId and deleted_at is not null",
            nativeQuery = true)
    int detachDeletedFromCategory(Long tenantId, Long categoryId);

    /** 加行锁读取商品（库存变动时使用，防止并发超卖）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
