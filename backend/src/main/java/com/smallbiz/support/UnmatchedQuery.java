package com.smallbiz.support;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 机器人没答上来的问题（只记录问题文字和时间，不记录任何商户数据）。 */
@Entity
@Table(name = "support_unmatched_queries")
public class UnmatchedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, length = 300)
    private String queryText;

    @Column(name = "normalized_text", nullable = false, length = 300)
    private String normalizedText;

    @Column(nullable = false)
    private int occurrences = 1;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean resolved;

    protected UnmatchedQuery() {
    }

    public UnmatchedQuery(String queryText, String normalizedText, Instant now) {
        this.queryText = queryText;
        this.normalizedText = normalizedText;
        this.lastSeenAt = now;
    }

    public void seenAgain(Instant now) {
        occurrences++;
        lastSeenAt = now;
        resolved = false;
    }

    public void resolve() {
        resolved = true;
    }

    public Long getId() {
        return id;
    }

    public String getQueryText() {
        return queryText;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isResolved() {
        return resolved;
    }
}
