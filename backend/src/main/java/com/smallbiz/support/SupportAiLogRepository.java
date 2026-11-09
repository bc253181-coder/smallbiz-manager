package com.smallbiz.support;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SupportAiLogRepository extends JpaRepository<SupportAiLog, Long> {

    long countByCreatedAtGreaterThanEqual(Instant since);

    Page<SupportAiLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<SupportAiLog> findByFlaggedTrueOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** 统计：[次数, 输入, 缓存读取, 输出]。 */
    @Query("select count(l), coalesce(sum(l.inputTokens),0), coalesce(sum(l.cacheReadTokens),0), coalesce(sum(l.outputTokens),0) "
            + "from SupportAiLog l where l.createdAt >= :since")
    Object[] usageSince(Instant since);

    @Modifying
    @Transactional
    @Query("delete from SupportAiLog l where l.createdAt < :before")
    int deleteOlderThan(Instant before);
}
