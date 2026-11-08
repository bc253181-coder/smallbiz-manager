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
}
