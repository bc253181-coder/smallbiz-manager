package com.smallbiz.pricing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    Optional<CustomerPrice> findByCustomerIdAndProductId(Long customerId, Long productId);

    List<CustomerPrice> findByCustomerIdOrderByIdDesc(Long customerId);

    List<CustomerPrice> findByProductIdOrderByIdDesc(Long productId);

    List<CustomerPrice> findByCustomerIdAndProductIdIn(Long customerId, Collection<Long> productIds);

    /** 设置协议价时锁住已有记录，避免两人同时修改互相覆盖却不报错。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPrice p where p.customerId = :customerId and p.productId = :productId")
    Optional<CustomerPrice> findForUpdate(Long customerId, Long productId);
}
