package com.smallbiz.support;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** AI 回答记录（平台级；不保存用户、店铺和 IP；90 天后自动删除）。 */
@Entity
@Table(name = "support_ai_logs")
public class SupportAiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    /** ANSWERED / HANDOFF / INVALID（模型回答没通过校验，已转人工）。 */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "faq_ids", length = 100)
    private String faqIds;

    @Column(nullable = false, length = 10)
    private String lang;

    @Column(length = 60)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    private Boolean helpful;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupportAiLog() {
    }

    public SupportAiLog(String question, String answer, String status, String faqIds, String lang, String model,
            long inputTokens, long cacheReadTokens, long outputTokens, long latencyMs, Instant createdAt) {
        this.question = question;
        this.answer = answer;
        this.status = status;
        this.faqIds = faqIds;
        this.lang = lang;
        this.model = model;
        this.inputTokens = (int) inputTokens;
        this.cacheReadTokens = (int) cacheReadTokens;
        this.outputTokens = (int) outputTokens;
        this.latencyMs = (int) latencyMs;
        this.createdAt = createdAt;
    }

    /** 用户只能评价一次。 */
    public void rate(boolean helpful) {
        if (this.helpful == null) {
            this.helpful = helpful;
        }
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getStatus() {
        return status;
    }

    public String getFaqIds() {
        return faqIds;
    }

    public String getLang() {
        return lang;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getCacheReadTokens() {
        return cacheReadTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Boolean getHelpful() {
        return helpful;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
