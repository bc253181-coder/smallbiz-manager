package com.smallbiz.billing;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.billing.CodeDispenserService.Dispensed;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** 快速发码页（不登录，靠密码）：/api/v1/quick-code */
@RestController
@RequestMapping("/api/v1/quick-code")
public class QuickCodeController {

    public record IssueRequest(@Size(max = 200) String password) {
    }

    public record Issued(String code) {
    }

    private final QuickCodeService service;

    public QuickCodeController(QuickCodeService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("enabled", service.enabled());
    }

    @PostMapping
    public Issued issue(@Valid @RequestBody IssueRequest req, HttpServletRequest http) {
        Dispensed d = service.issue(req.password(), http.getRemoteAddr());
        return new Issued(d.code());
    }
}
