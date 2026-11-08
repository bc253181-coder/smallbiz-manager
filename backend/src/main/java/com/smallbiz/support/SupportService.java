package com.smallbiz.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.config.PlatformProperties;

/**
 * 话术客服机器人：关键词匹配，不调用大模型（零成本、回答可控、不会胡编）。
 * 评分 = 命中关键词的长度之和（越具体的词权重越高）；低于阈值视为未匹配并记录，返回人工客服信息。
 */
@Service
public class SupportService {

    static final int MIN_SCORE = 2;
    static final int MAX_ASKS_PER_MINUTE = 20;

    public record Answer(boolean matched, Long faqId, String question, String answer, String actionLabel,
            String actionPath, Handoff handoff) {
    }

    public record Handoff(String wechatId, String tip) {
    }

    private final SupportFaqRepository faqs;
    private final UnmatchedQueryRepository unmatched;
    private final PlatformProperties platform;
    private final Map<String, Deque<Instant>> rate = new ConcurrentHashMap<>();

    public SupportService(SupportFaqRepository faqs, UnmatchedQueryRepository unmatched, PlatformProperties platform) {
        this.faqs = faqs;
        this.unmatched = unmatched;
        this.platform = platform;
    }

    public Handoff handoff() {
        return new Handoff(platform.supportWechatId(), "添加时请备注：店铺名 + 问题简述，我们会尽快回复。");
    }

    @Transactional(readOnly = true)
    public List<SupportFaq> quickReplies() {
        return faqs.findByEnabledTrueOrderBySortOrderAsc().stream().filter(SupportFaq::isQuickReply).toList();
    }

    @Transactional
    public Answer ask(String clientKey, String message) {
        throttle(clientKey);
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "请输入问题");
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        String norm = normalize(text);
        SupportFaq best = null;
        int bestScore = 0;
        for (SupportFaq f : faqs.findByEnabledTrueOrderBySortOrderAsc()) {
            int score = score(norm, f);
            if (score > bestScore) {
                best = f;
                bestScore = score;
            }
        }
        if (best != null && bestScore >= MIN_SCORE) {
            return new Answer(true, best.getId(), best.getQuestion(), best.getAnswer(), best.getActionLabel(),
                    best.getActionPath(), null);
        }
        recordUnmatched(text, norm);
        return new Answer(false, null, null, null, null, null, handoff());
    }

    /** 直接按话术 id 回答（点击快捷问题时）。 */
    @Transactional(readOnly = true)
    public Answer answer(Long faqId) {
        SupportFaq f = faqs.findById(faqId).filter(SupportFaq::isEnabled)
                .orElseThrow(() -> ApiException.notFound("FAQ_NOT_FOUND", "问题不存在"));
        return new Answer(true, f.getId(), f.getQuestion(), f.getAnswer(), f.getActionLabel(), f.getActionPath(), null);
    }

    @Transactional
    public void feedback(String clientKey, Long faqId, boolean helpful) {
        throttle(clientKey);
        faqs.findById(faqId).ifPresent(f -> f.feedback(helpful));
    }

    static int score(String normalizedQuery, SupportFaq f) {
        int score = 0;
        for (String kw : f.getKeywords().split("[,，]")) {
            String k = normalize(kw);
            if (k.length() >= 1 && normalizedQuery.contains(k)) {
                score += k.length();
            }
        }
        String q = normalize(f.getQuestion());
        if (!q.isEmpty() && (normalizedQuery.contains(q) || q.contains(normalizedQuery) && normalizedQuery.length() >= 4)) {
            score += 4;
        }
        return score;
    }

    /** 小写、去掉空白和标点，便于匹配和合并相同问题。 */
    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。？！、；：“”‘’（）《》…—]", "");
    }

    private void recordUnmatched(String text, String norm) {
        if (norm.isEmpty()) {
            return;
        }
        String key = norm.length() > 300 ? norm.substring(0, 300) : norm;
        Instant now = Instant.now();
        unmatched.findByNormalizedText(key).ifPresentOrElse(u -> u.seenAgain(now), () -> {
            try {
                unmatched.saveAndFlush(new UnmatchedQuery(text, key, now));
            } catch (DataIntegrityViolationException ignored) {
                // 并发时另一个请求已经插入，忽略即可
            }
        });
    }

    /** 防刷：同一来源每分钟最多 20 次。 */
    private void throttle(String clientKey) {
        Instant now = Instant.now();
        Deque<Instant> q = rate.computeIfAbsent(clientKey == null ? "?" : clientKey, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_ASKS_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "提问太频繁了，请稍后再试");
            }
            q.addLast(now);
        }
    }
}
