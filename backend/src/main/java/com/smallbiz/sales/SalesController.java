package com.smallbiz.sales;

import java.time.LocalDate;
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

import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.sales.SalesDtos.CancelRequest;
import com.smallbiz.sales.SalesDtos.CustomerStats;
import com.smallbiz.sales.SalesDtos.SaleRequest;
import com.smallbiz.sales.SalesDtos.SaleSummary;
import com.smallbiz.sales.SalesDtos.SaleView;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;

import jakarta.validation.Valid;

/** 销售：所有成员可开单和查看；取消限店主/管理员；店员看不到成本和毛利。 */
@RestController
@RequestMapping("/api/v1")
public class SalesController {

    private static final Map<String, String> SORTABLE = Map.of("orderDate", "orderDate", "totalAmount", "totalAmount");

    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    private static boolean hideCost() {
        return CurrentUser.roleOrNull() == MemberRole.STAFF;
    }

    @GetMapping("/sales-orders")
    public PageResponse<SaleSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SalesOrder.Status status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Long tenantId = CurrentUser.requireTenantId();
        return service.list(tenantId, new SalesService.Filter(q, status, customerId, from, to),
                Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "orderDate", "id")));
    }

    @GetMapping("/sales-orders/{id}")
    public SaleView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id, hideCost());
    }

    @PostMapping("/sales-orders")
    public ResponseEntity<SaleView> create(@Valid @RequestBody SaleRequest req) {
        Long tenantId = CurrentUser.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, tenantId, CurrentUser.userId(), hideCost()));
    }

    @PostMapping("/sales-orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SaleView cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        CurrentUser.requireTenantId();
        return service.cancel(id, req == null ? null : req.reason(), CurrentUser.userId());
    }

    @GetMapping("/customers/{id}/stats")
    @com.smallbiz.billing.RequiresFeature(com.smallbiz.billing.Feature.CUSTOMERS)
    public CustomerStats customerStats(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.customerStats(id);
    }
}
