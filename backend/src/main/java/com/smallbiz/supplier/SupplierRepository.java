package com.smallbiz.supplier;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    /** 导入判重：按名称。 */
    Optional<Supplier> findFirstByNameOrderByIdAsc(String name);
}
