package com.smallbiz.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ActivationCode c where c.codeHash = :hash")
    Optional<ActivationCode> findByHashForUpdate(String hash);

    long countByBatchIdAndStatus(Long batchId, ActivationCode.Status status);

    List<ActivationCode> findByBatchIdOrderByIdAsc(Long batchId);

    long countByOrderRef(String orderRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ActivationCode c where c.orderRef = :orderRef order by c.id")
    List<ActivationCode> findByOrderRefForUpdate(String orderRef);

    List<ActivationCode> findTop100ByDispensedAtIsNotNullOrderByDispensedAtDescIdDesc();

    /** 发码台搜索：订单号包含 q，或激活码后 4 位等于 hint。 */
    @Query("select c from ActivationCode c where (c.orderRef is not null and lower(c.orderRef) like lower(concat('%', :q, '%'))) "
            + "or c.codeHint = :hint order by c.id desc limit 100")
    List<ActivationCode> searchDispenser(String q, String hint);
}