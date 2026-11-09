package com.smallbiz.billing.membership;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface AccountUsageRepository extends JpaRepository<AccountUsage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AccountUsage u where u.userId = :userId")
    Optional<AccountUsage> findForUpdate(Long userId);

    /** 没有记录时建一行（并发时只会成功一次）。 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO account_usage (user_id, stores_created, products_created, updated_at) "
            + "VALUES (:userId, 0, 0, UTC_TIMESTAMP(6))", nativeQuery = true)
    void ensureRow(Long userId);
}
