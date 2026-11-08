package com.smallbiz.support;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 客服话术（平台级，所有商户共用）。 */
@Entity
@Table(name = "support_faqs")
public class SupportFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 200)
    private String question;

    /** 关键词和同义词，逗号分隔。 */
    @Column(nullable = false, length = 500)
    private String keywords;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "action_label", length = 30)
    private String actionLabel;

    @Column(name = "action_path", length = 100)
    private String actionPath;

    @Column(name = "is_quick_reply", nullable = false)
    private boolean quickReply;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "unhelpful_count", nullable = false)
    private int unhelpfulCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupportFaq() {
    }

    public void update(String category, String question, String keywords, String answer, String actionLabel,
            String actionPath, boolean quickReply, int sortOrder, boolean enabled) {
        this.category = category;
        this.question = question;
        this.keywords = keywords;
        this.answer = answer;
        this.actionLabel = actionLabel;
        this.actionPath = actionPath;
        this.quickReply = quickReply;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    public void feedback(boolean helpful) {
        if (helpful) {
            helpfulCount++;
        } else {
            unhelpfulCount++;
        }
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getQuestion() {
        return question;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getAnswer() {
        return answer;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getActionPath() {
        return actionPath;
    }

    public boolean isQuickReply() {
        return quickReply;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public int getUnhelpfulCount() {
        return unhelpfulCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
