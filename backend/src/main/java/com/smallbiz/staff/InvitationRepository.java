package com.smallbiz.staff;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :hash")
    Optional<Invitation> findByTokenHashForUpdate(String hash);

    Optional<Invitation> findByIdAndTenantId(Long id, Long tenantId);

    /** 未使用、未撤销、未过期的邀请（占员工名额）。 */
    @Query("select i from Invitation i where i.tenantId = :tenantId "
            + "and i.status = com.smallbiz.staff.Invitation.Status.PENDING and i.expiresAt > :now order by i.id desc")
    List<Invitation> findPending(Long tenantId, Instant now);
}
