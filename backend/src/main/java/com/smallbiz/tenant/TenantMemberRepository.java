package com.smallbiz.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMemberRepository extends JpaRepository<TenantMember, Long> {

    List<TenantMember> findByUserIdAndStatusOrderByIdAsc(Long userId, TenantMember.Status status);

    Optional<TenantMember> findByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, TenantMember.Status status);
}
