package com.smallbiz.receivable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.RequiresFeature;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.receivable.ReceivableDtos.CustomerReceivable;
import com.smallbiz.receivable.ReceivableDtos.CustomerRow;
import com.smallbiz.receivable.ReceivableDtos.PaymentRequest;
import com.smallbiz.receivable.ReceivableDtos.PaymentView;
import com.smallbiz.receivable.ReceivableDtos.Statement;
import com.smallbiz.receivable.ReceivableDtos.Summary;
import com.smallbiz.receivable.ReceivableDtos.VoidRequest;
import com.smallbiz.report.CsvWriter;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * 应收账款（专业版）。店员可以查看单个客户的欠款并登记收款；
 * 汇总、收款记录、作废、对账单、导出限店主/管理员。
 */
@RestController
@RequestMapping("/api/v1")
@RequiresFeature(Feature.RECEIVABLES)
public class ReceivableController {

    private static final Map<String, String> SORTABLE = Map.of("receivedAt", "receivedAt", "amount", "amount");

    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    @GetMapping("/receivables/summary")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Summary summary() {
        return service.summary(CurrentUser.requireTenantId());
    }

    @GetMapping("/receivables/customers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<CustomerRow> customers(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minAgeDays) {
        return service.customers(CurrentUser.requireTenantId(), q, minAgeDays);
    }

    @GetMapping("/receivables/customers/{customerId}")
    public CustomerReceivable customer(@PathVariable Long customerId) {
        return service.customer(CurrentUser.requireTenantId(), customerId);
    }

    @GetMapping("/receivables/export")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minAgeDays) {
        List<List<Object>> rows = new ArrayList<>();
        for (CustomerRow r : service.customers(CurrentUser.requireTenantId(), q, minAgeDays)) {
            rows.add(List.of(r.customerName(), r.phone() == null ? "" : r.phone(), r.outstanding(), r.openOrderCount(),
                    r.oldestDays(), r.aging().days0To30(), r.aging().days31To60(), r.aging().days61To90(),
                    r.aging().over90()));
        }
        return CsvWriter.response("应收账款.csv", List.of("客户", "电话", "欠款", "未结清单数", "最早欠款天数", "0-30天",
                "31-60天", "61-90天", "90天以上"), rows);
    }

    @PostMapping("/customer-payments")
    public ResponseEntity<PaymentView> receive(@Valid @RequestBody PaymentRequest req) {
        Long tenantId = CurrentUser.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.receive(req, tenantId, CurrentUser.userId()));
    }

    @GetMapping("/customer-payments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PageResponse<PaymentView> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) CustomerPayment.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Long tenantId = CurrentUser.requireTenantId();
        return service.list(tenantId, new ReceivableService.PaymentFilter(customerId, from, to, status),
                Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "receivedAt", "id")));
    }

    @GetMapping("/customer-payments/{id}")
    public PaymentView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id);
    }

    @PostMapping("/customer-payments/{id}/void")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PaymentView voidPayment(@PathVariable Long id, @Valid @RequestBody VoidRequest req) {
        CurrentUser.requireTenantId();
        return service.voidPayment(id, req.reason(), CurrentUser.userId());
    }

    @GetMapping("/customers/{customerId}/statement")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Statement statement(@PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.statement(CurrentUser.requireTenantId(), customerId, from, to);
    }
}
