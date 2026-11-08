package com.smallbiz.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Long> {
    List<PlanPrice> findAllByOrderByCurrencyAscSortOrderAsc();

    List<PlanPrice> findByCurrencyAndEnabledTrueOrderBySortOrderAsc(String currency);
}
