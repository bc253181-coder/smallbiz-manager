package com.smallbiz.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscIdAsc();

    List<Category> findByParentId(Long parentId);

    boolean existsByParentId(Long parentId);

    Optional<Category> findFirstByParentIdIsNullAndName(String name);

    Optional<Category> findFirstByParentIdAndName(Long parentId, String name);
}
