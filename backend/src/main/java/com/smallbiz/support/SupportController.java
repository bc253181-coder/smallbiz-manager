package com.smallbiz.support;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public record Welcome(String greeting, List<QuickReply> quickReplies) {
    }

    public record QuickReply(Long id, String question) {
    }

    public record AskRequest(@NotBlank(message = "请输入问题") @Size(max = 300, message = "问题最多 300 字") String message) {
    }

    public record FeedbackRequest(@NotNull Boolean helpful) {
    }

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @GetMapping("/welcome")
    public Welcome welcome() {
        return new Welcome("你好！我是 SmallBiz 小助手，可以回答使用和套餐方面的问题。也可以直接输入你的问题。",
                service.quickReplies().stream().map(f -> new QuickReply(f.getId(), f.getQuestion())).toList());
    }

    @PostMapping("/ask")
    public Answer ask(@Valid @RequestBody AskRequest req, HttpServletRequest http) {
        return service.ask(http.getRemoteAddr(), req.message());
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

    @GetMapping("/contact")
    public Handoff contact() {
        return service.handoff();
    }
}
