package com.smallbiz.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.support.AiSupportProperties;
import com.smallbiz.support.SupportAi;
import com.smallbiz.support.SupportAiLog;
import com.smallbiz.support.SupportAiLogRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 平台后台：AI 客服回答记录、用量与估算费用。 */
@RestController
@RequestMapping("/api/v1/admin/support/ai")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminSupportAiController {

    public record LogRow(Long id, String question, String answer, String status, List<Long> faqIds, String lang,
            String model, int inputTokens, int cacheReadTokens, int outputTokens, int latencyMs, Boolean helpful,
            boolean flagged, Instant createdAt) {

        static LogRow of(SupportAiLog l) {
            List<Long> ids = l.getFaqIds() == null ? List.of()
                    : java.util.Arrays.stream(l.getFaqIds().split(",")).map(Long::valueOf).toList();
            return new LogRow(l.getId(), l.getQuestion(), l.getAnswer(), l.getStatus(), ids, l.getLang(), l.getModel(),
                    l.getInputTokens(), l.getCacheReadTokens(), l.getOutputTokens(), l.getLatencyMs(), l.getHelpful(),
                    l.isFlagged(), l.getCreatedAt());
        }
    }

    public record Usage(long calls, long inputTokens, long cacheReadTokens, long outputTokens, BigDecimal estimatedUsd) {
    }

    public record Stats(boolean enabled, String model, int dailyLimit, int perIpDailyLimit, Usage today, Usage month) {
    }

    public record FlagRequest(@NotNull Boolean flagged) {
    }

    private final SupportAiLogRepository logs;
    private final SupportAi ai;
    private final AiSupportProperties props;

    public AdminSupportAiController(SupportAiLogRepository logs, SupportAi ai, AiSupportProperties props) {
        this.logs = logs;
        this.ai = ai;
        this.props = props;
    }

    @GetMapping("/stats")
    public Stats stats() {
        LocalDate today = LocalDate.now(com.smallbiz.support.SupportService.DAY_ZONE);
        Instant dayStart = today.atStartOfDay(com.smallbiz.support.SupportService.DAY_ZONE).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(com.smallbiz.support.SupportService.DAY_ZONE).toInstant();
        return new Stats(ai.available(), ai.model(), props.dailyLimit(), props.perIpDailyLimit(), usage(dayStart),
                usage(monthStart));
    }

    private Usage usage(Instant since) {
        Object[] row = logs.usageSince(since);
        if (row.length == 1 && row[0] instanceof Object[] inner) {
            row = inner;
        }
        long calls = ((Number) row[0]).longValue();
        long in = ((Number) row[1]).longValue();
        long cache = ((Number) row[2]).longValue();
        long out = ((Number) row[3]).longValue();
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        BigDecimal usd = props.inputPrice().multiply(BigDecimal.valueOf(in))
                .add(props.cacheReadPrice().multiply(BigDecimal.valueOf(cache)))
                .add(props.outputPrice().multiply(BigDecimal.valueOf(out)))
                .divide(million, 4, RoundingMode.HALF_UP);
        return new Usage(calls, in, cache, out, usd);
    }

    @GetMapping("/logs")
    public PageResponse<LogRow> logs(@RequestParam(defaultValue = "false") boolean flagged,
            @RequestParam(defaultValue = "0") int page) {
        PageRequest p = PageRequest.of(Math.max(page, 0), 50);
        return PageResponse.of(flagged ? logs.findByFlaggedTrueOrderByCreatedAtDescIdDesc(p)
                : logs.findAllByOrderByCreatedAtDescIdDesc(p), LogRow::of);
    }

    @PostMapping("/logs/{id}/flag")
    @Transactional
    public ResponseEntity<Void> flag(@PathVariable Long id, @Valid @RequestBody FlagRequest req) {
        SupportAiLog l = logs.findById(id).orElseThrow(() -> ApiException.notFound("AI_LOG_NOT_FOUND", "记录不存在"));
        l.setFlagged(req.flagged());
        return ResponseEntity.noContent().build();
    }
}
