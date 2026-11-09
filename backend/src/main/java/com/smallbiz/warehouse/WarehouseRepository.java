package com.smallbiz.warehouse;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @Query("select w from Warehouse w order by w.isDefault desc, w.sortOrder asc, w.id asc")
    List<Warehouse> findAllOrdered();

    @Query("select w from Warehouse w where w.isDefault = true")
    Optional<Warehouse> findDefault();

    boolean existsByName(String name);

    long countByActiveTrue();
}
