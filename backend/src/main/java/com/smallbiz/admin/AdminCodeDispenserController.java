package com.smallbiz.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.billing.CodeDispenserService;
import com.smallbiz.billing.CodeDispenserService.Dispensed;
import com.smallbiz.billing.CodeDispenserService.Row;
import com.smallbiz.billing.CodeDispenserService.Stock;
import com.smallbiz.billing.CodeDispenserService.VoidResult;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 平台后台：发码台（按小红书订单取出高级永久会员激活码）。 */
@RestController
@RequestMapping("/api/v1/admin/code-dispenser")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminCodeDispenserController {

    public record DispenseRequest(@NotBlank(message = "请填写小红书订单号") @Size(max = 100) String orderRef,
            @Min(1) @Max(CodeDispenserService.MAX_PER_ORDER) Integer quantity, Boolean confirmRepeat) {
    }

    public record OrderRequest(@NotBlank(message = "请填写小红书订单号") @Size(max = 100) String orderRef) {
    }

    private final CodeDispenserService dispenser;

    public AdminCodeDispenserController(CodeDispenserService dispenser) {
        this.dispenser = dispenser;
    }

    @GetMapping("/stock")
    public Stock stock() {
        return dispenser.stock();
    }

    @PostMapping("/dispense")
    public List<Dispensed> dispense(@Valid @RequestBody DispenseRequest req) {
        return dispenser.dispense(req.orderRef(), req.quantity() == null ? 1 : req.quantity(),
                Boolean.TRUE.equals(req.confirmRepeat()), CurrentUser.userId());
    }

    @GetMapping("/codes")
    public List<Row> search(@RequestParam(required = false) String q) {
        return dispenser.search(q);
    }

    /** 最近被使用的码（默认 7 天，最多 90 天）。 */
    @GetMapping("/redeemed")
    public List<Row> redeemed(@RequestParam(defaultValue = "7") int days) {
        return dispenser.recentlyRedeemed(days);
    }

    @PostMapping("/codes/{id}/reveal")
    public Dispensed reveal(@PathVariable Long id) {
        return dispenser.reveal(id, CurrentUser.userId());
    }

    @PostMapping("/void-order")
    public VoidResult voidOrder(@Valid @RequestBody OrderRequest req) {
        return dispenser.voidOrder(req.orderRef(), CurrentUser.userId());
    }
}