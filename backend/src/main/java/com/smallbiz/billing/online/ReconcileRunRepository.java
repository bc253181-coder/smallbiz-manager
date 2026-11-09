package com.smallbiz.billing.online;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconcileRunRepository extends JpaRepository<ReconcileRun, Long> {

    Optional<ReconcileRun> findTopByOrderByIdDesc();
}
