package com.smallbiz.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.common.ApiException;
import com.smallbiz.config.PlatformProperties;

/**
 * 客服：先用关键词匹配话术（零成本、立即返回）；关键词不确定、追问、英文提问时交给 AI，
 * AI 只能根据话术库回答，答不了转人工。没有配置 AI、超出限额、AI 出错时退回关键词匹配。
 *
 * <p>关键词评分 = 命中关键词的长度之和（越具体的词权重越高）。
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    static final int MIN_SCORE = 2;
    /** 有 AI 时，关键词得分达到这个值才直接回答（否则交给 AI 理解）。 */
    static final int STRONG_SCORE = 6;
    static final int MAX_ASKS_PER_MINUTE = 20;
    static final int MAX_HISTORY_TURNS = 6;
    static final int MAX_TURN_CHARS = 600;
    static final Duration LOG_RETENTION = Duration.ofDays(90);
    /** 「每天」按北京时间计算。 */
    public static final ZoneId DAY_ZONE = ZoneId.of("Asia/Shanghai");

    public enum Source {
        KEYWORD, AI
    }

    public record Answer(boolean matched, Long faqId, String question, String answer, String actionLabel,
            String actionPath, Handoff handoff, Source source, Long logId) {

        static Answer faq(SupportFaq f) {
            return new Answer(true, f.getId(), f.getQuestion(), f.getAnswer(), f.getActionLabel(), f.getActionPath(),
                    null, Source.KEYWORD, null);
        }
    }

    public record Handoff(String wechatId, String tip) {
    }

    private final SupportFaqRepository faqs;
    private final UnmatchedQueryRepository unmatched;
    private final SupportAiLogRepository aiLogs;
    private final SupportPrompt prompt;
    private final SupportAi ai;
    private final AiSupportProperties aiProps;
    private final PlatformProperties platform;
    private final TransactionTemplate tx;
    private final Map<String, Deque<Instant>> rate = new ConcurrentHashMap<>();
    private final Map<String, Integer> aiPerIp = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> aiPerIpDay = new AtomicReference<>(LocalDate.now(DAY_ZONE));
    private final AtomicReference<LocalDate> lastCleanup = new AtomicReference<>();

    public SupportService(SupportFaqRepository faqs, UnmatchedQueryRepository unmatched, SupportAiLogRepository aiLogs,
            SupportPrompt prompt, SupportAi ai, AiSupportProperties aiProps, PlatformProperties platform,
            PlatformTransactionManager txManager) {
        this.faqs = faqs;
        this.unmatched = unmatched;
        this.aiLogs = aiLogs;
        this.prompt = prompt;
        this.ai = ai;
        this.aiProps = aiProps;
        this.platform = platform;
        this.tx = new TransactionTemplate(txManager);
    }

    public boolean aiEnabled() {
        return ai.available();
    }

    public Handoff handoff(String lang) {
        String tip = isEnglish(lang) ? "When adding us, please note your store name and a short description of the issue. We'll reply as soon as possible."
                : "添加时请备注：店铺名 + 问题简述，我们会尽快回复。";
        return new Handoff(platform.supportWechatId(), tip);
    }

    @Transactional(readOnly = true)
    public List<SupportFaq> quickReplies() {
        return faqs.findByEnabledTrueOrderBySortOrderAsc().stream().filter(SupportFaq::isQuickReply).toList();
    }

    /** 回答问题（不在数据库事务中调用 AI，避免长时间占用连接）。 */
    public Answer ask(String clientKey, String message, List<SupportAi.Turn> history, String lang) {
        throttle(clientKey);
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "请输入问题");
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        String language = isEnglish(lang) ? "en" : "zh-CN";
        String norm = normalize(text);
        List<SupportAi.Turn> turns = sanitize(history);

        SupportFaq best = null;
        int bestScore = 0;
        for (SupportFaq f : faqs.findByEnabledTrueOrderBySortOrderAsc()) {
            int score = score(norm, f);
            if (score > bestScore) {
                best = f;
                bestScore = score;
            }
        }

        if (ai.available()) {
            boolean strong = best != null && bestScore >= STRONG_SCORE && turns.isEmpty() && !isEnglish(language);
            if (strong) {
                return Answer.faq(best);
            }
            if (tryAcquireAiQuota(clientKey)) {
                try {
                    return askAi(text, norm, turns, language);
                } catch (RuntimeException e) {
                    log.warn("AI 客服调用失败，退回关键词匹配：{}", e.toString());
                }
            }
        }
        if (best != null && bestScore >= MIN_SCORE) {
            return Answer.faq(best);
        }
        recordUnmatched(text, norm);
        return new Answer(false, null, null, null, null, null, handoff(language), Source.KEYWORD, null);
    }

    private Answer askAi(String text, String norm, List<SupportAi.Turn> turns, String lang) {
        List<SupportFaq> enabled = prompt.enabledFaqs();
        Map<Long, SupportFaq> byId = enabled.stream().collect(Collectors.toMap(SupportFaq::getId, Function.identity()));
        long start = System.nanoTime();
        SupportAi.Result r = ai.answer(prompt.build(enabled), turns, text);
        long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // 校验：引用的话术必须真实存在且启用；按钮只能来自被引用的话术
        Set<Long> cited = new LinkedHashSet<>();
        for (Long id : r.faqIds()) {
            if (id != null && byId.containsKey(id)) {
                cited.add(id);
            }
        }
        String answer = r.answer() == null ? "" : r.answer().strip();
        boolean valid = r.status() == SupportAi.Status.ANSWERED && !answer.isEmpty() && answer.length() <= 1500
                && !cited.isEmpty() && r.faqIds().stream().allMatch(id -> id != null && byId.containsKey(id));
        String status = r.status() == SupportAi.Status.HANDOFF ? "HANDOFF" : valid ? "ANSWERED" : "INVALID";
        SupportAiLog saved = aiLogs.save(new SupportAiLog(text, answer.isEmpty() ? null : answer, status,
                cited.isEmpty() ? null : cited.stream().map(String::valueOf).collect(Collectors.joining(",")), lang,
                ai.model(), r.inputTokens(), r.cacheReadTokens(), r.outputTokens(), latency, Instant.now()));
        cleanupOldLogs();

        if (!valid) {
            recordUnmatched(text, norm);
            // 模型自己说转人工时，显示它的简短说明；回答没通过校验时不显示
            String note = "HANDOFF".equals(status) && !answer.isEmpty() && answer.length() <= 300 ? answer : null;
            return new Answer(false, null, null, note, null, null, handoff(lang), Source.AI, saved.getId());
        }
        SupportFaq action = byId.get(r.actionFaqId());
        if (action == null || !cited.contains(action.getId()) || action.getActionPath() == null) {
            action = cited.stream().map(byId::get).filter(f -> f.getActionPath() != null).findFirst().orElse(null);
        }
        // 按钮文字在英文界面由前端翻译（话术里的按钮名是中文）
        return new Answer(true, null, null, answer, action == null ? null : action.getActionLabel(),
                action == null ? null : action.getActionPath(), null, Source.AI, saved.getId());
    }

    /** 直接按话术 id 回答（点击快捷问题时）。 */
    @Transactional(readOnly = true)
    public Answer answer(Long faqId) {
        SupportFaq f = faqs.findById(faqId).filter(SupportFaq::isEnabled)
                .orElseThrow(() -> ApiException.notFound("FAQ_NOT_FOUND", "问题不存在"));
        return Answer.faq(f);
    }

    @Transactional
    public void feedback(String clientKey, Long faqId, boolean helpful) {
        throttle(clientKey);
        faqs.findById(faqId).ifPresent(f -> f.feedback(helpful));
    }

    @Transactional
    public void aiFeedback(String clientKey, Long logId, boolean helpful) {
        throttle(clientKey);
        aiLogs.findById(logId).ifPresent(l -> l.rate(helpful));
    }

    /** 历史对话：只保留最近几轮，必须用户开头、一问一答交替，过长的截断。 */
    static List<SupportAi.Turn> sanitize(List<SupportAi.Turn> history) {
        List<SupportAi.Turn> out = new ArrayList<>();
        if (history == null) {
            return out;
        }
        List<SupportAi.Turn> recent = history.size() > MAX_HISTORY_TURNS
                ? history.subList(history.size() - MAX_HISTORY_TURNS, history.size()) : history;
        for (SupportAi.Turn t : recent) {
            if (t == null || t.text() == null || t.text().isBlank()) {
                continue;
            }
            String s = t.text().strip();
            if (s.length() > MAX_TURN_CHARS) {
                s = s.substring(0, MAX_TURN_CHARS);
            }
            if (out.isEmpty() && !t.fromUser()) {
                continue;
            }
            if (!out.isEmpty() && out.get(out.size() - 1).fromUser() == t.fromUser()) {
                out.set(out.size() - 1, new SupportAi.Turn(t.fromUser(), out.get(out.size() - 1).text() + "\n" + s));
            } else {
                out.add(new SupportAi.Turn(t.fromUser(), s));
            }
        }
        // 当前问题是用户说的：历史必须以助手回答结尾
        if (!out.isEmpty() && out.get(out.size() - 1).fromUser()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static boolean isEnglish(String lang) {
        return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("en");
    }

    /** 每个 IP 每天、全平台每天的 AI 次数上限；超出后退回关键词匹配。 */
    private boolean tryAcquireAiQuota(String clientKey) {
        LocalDate today = LocalDate.now(DAY_ZONE);
        LocalDate day = aiPerIpDay.get();
        if (!today.equals(day) && aiPerIpDay.compareAndSet(day, today)) {
            aiPerIp.clear();
        }
        Instant startOfDay = today.atStartOfDay(DAY_ZONE).toInstant();
        if (aiLogs.countByCreatedAtGreaterThanEqual(startOfDay) >= aiProps.dailyLimit()) {
            return false;
        }
        String key = clientKey == null ? "?" : clientKey;
        int used = aiPerIp.merge(key, 1, Integer::sum);
        if (used > aiProps.perIpDailyLimit()) {
            aiPerIp.merge(key, -1, Integer::sum);
            return false;
        }
        return true;
    }

    /** AI 记录保存 90 天：每天第一次写入时清理一次。 */
    private void cleanupOldLogs() {
        LocalDate today = LocalDate.now(DAY_ZONE);
        LocalDate last = lastCleanup.get();
        if (!today.equals(last) && lastCleanup.compareAndSet(last, today)) {
            purgeExpiredLogs();
        }
    }

    int purgeExpiredLogs() {
        int n = aiLogs.deleteOlderThan(Instant.now().minus(LOG_RETENTION));
        if (n > 0) {
            log.info("已删除 {} 条超过 90 天的 AI 客服记录", n);
        }
        return n;
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
        try {
            tx.executeWithoutResult(s -> unmatched.findByNormalizedText(key).ifPresentOrElse(u -> u.seenAgain(now),
                    () -> unmatched.saveAndFlush(new UnmatchedQuery(text, key, now))));
        } catch (DataIntegrityViolationException ignored) {
            // 并发时另一个请求已经插入，忽略即可
        }
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
