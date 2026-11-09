package com.smallbiz.support;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.support.SupportService.Answer;
import com.smallbiz.support.SupportService.Handoff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 客服机器人：无需登录（登录页也能问"忘记密码"），按来源 IP 限速。 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    public record Welcome(String greeting, List<QuickReply> quickReplies, boolean ai) {
    }

    public record QuickReply(Long id, String question) {
    }

    /** 最近几轮对话（只在浏览器内存里，关闭客服即清空）：role 为 user 或 bot。 */
    public record HistoryItem(String role, @Size(max = 2000) String text) {
    }

    public record AskRequest(@NotBlank(message = "请输入问题") @Size(max = 300, message = "问题最多 300 字") String message,
            @Size(max = 20) List<@Valid HistoryItem> history, @Size(max = 10) String lang) {
    }

    public record FeedbackRequest(@NotNull Boolean helpful) {
    }

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @GetMapping("/welcome")
    public Welcome welcome(@RequestParam(required = false) String lang) {
        boolean ai = service.aiEnabled();
        if (SupportService.isEnglish(lang)) {
            // 话术库是中文：英文界面不显示中文快捷问题，有 AI 时直接用英文提问
            return new Welcome(ai
                    ? "Hi! I'm the SmallBiz assistant. Ask me anything about using SmallBiz or our plans."
                    : "Hi! I'm the SmallBiz assistant. I can answer common questions (in Chinese). For anything else, tap \"Human support\".",
                    ai ? List.of() : quickReplies(), ai);
        }
        return new Welcome(ai ? "你好！我是 SmallBiz AI 小助手，可以回答使用和套餐方面的问题，直接输入你的问题就行。"
                : "你好！我是 SmallBiz 小助手，可以回答使用和套餐方面的问题。也可以直接输入你的问题。", quickReplies(), ai);
    }

    private List<QuickReply> quickReplies() {
        return service.quickReplies().stream().map(f -> new QuickReply(f.getId(), f.getQuestion())).toList();
    }

    @PostMapping("/ask")
    public Answer ask(@Valid @RequestBody AskRequest req, HttpServletRequest http) {
        List<SupportAi.Turn> history = req.history() == null ? List.of()
                : req.history().stream().filter(h -> h != null && h.text() != null)
                        .map(h -> new SupportAi.Turn(!"bot".equals(h.role()), h.text())).toList();
        return service.ask(http.getRemoteAddr(), req.message(), history, req.lang());
    }

    @GetMapping("/faqs/{id}")
    public Answer answer(@PathVariable Long id) {
        return service.answer(id);
    }

    @PostMapping("/faqs/{id}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest req,
            HttpServletRequest http) {
        service.feedback(http.getRemoteAddr(), id, req.helpful());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ai/{logId}/feedback")
    public ResponseEntity<Void> aiFeedback(@PathVariable Long logId, @Valid @RequestBody FeedbackRequest req,
            HttpServletRequest http) {
        service.aiFeedback(http.getRemoteAddr(), logId, req.helpful());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contact")
    public Handoff contact(@RequestParam(required = false) String lang) {
        return service.handoff(lang);
    }
}
