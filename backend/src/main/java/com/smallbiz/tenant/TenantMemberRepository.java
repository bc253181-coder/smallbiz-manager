package com.smallbiz.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantMemberRepository extends JpaRepository<TenantMember, Long> {

    List<TenantMember> findByUserIdAndStatusOrderByIdAsc(Long userId, TenantMember.Status status);

    Optional<TenantMember> findByTenantIdAndUserIdAndStatus(Long tenantId, Long userId, TenantMember.Status status);

    Optional<TenantMember> findByTenantIdAndUserId(Long tenantId, Long userId);

    List<TenantMember> findByTenantIdAndStatusOrderByIdAsc(Long tenantId, TenantMember.Status status);

    /** 店主以外的在职成员数（占员工名额）。 */
    @Query("select count(m) from TenantMember m where m.tenantId = :tenantId and "
            + "m.status = com.smallbiz.tenant.TenantMember.Status.ACTIVE and m.role <> com.smallbiz.tenant.MemberRole.OWNER")
    long countStaff(Long tenantId);
}
