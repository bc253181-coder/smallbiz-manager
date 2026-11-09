package com.smallbiz.customer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /** 导入判重：先按电话，其次按名称。 */
    Optional<Customer> findFirstByPhoneOrderByIdAsc(String phone);

    Optional<Customer> findFirstByNameOrderByIdAsc(String name);
}
