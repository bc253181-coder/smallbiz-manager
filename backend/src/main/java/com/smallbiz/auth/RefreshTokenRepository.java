package com.smallbiz.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 加行锁：同一个刷新令牌并发使用时只有一个请求能成功轮换。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(String hash);

    @Modifying
    /** 作废该用户所有刷新令牌，包括仍在轮换宽限期内的旧令牌。 */
    @Query("update RefreshToken t set t.revokedAt = coalesce(t.revokedAt, :now), t.rotatedAt = null "
            + "where t.userId = :userId and (t.revokedAt is null or t.rotatedAt is not null)")
    int revokeAllForUser(Long userId, Instant now);
}
