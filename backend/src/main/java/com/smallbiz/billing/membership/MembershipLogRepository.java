package com.smallbiz.billing.membership;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipLogRepository extends JpaRepository<MembershipLog, Long> {

    List<MembershipLog> findTop20ByUserIdOrderByIdDesc(Long userId);

    long countByUserIdAndNewMembership(Long userId, String newMembership);
}
