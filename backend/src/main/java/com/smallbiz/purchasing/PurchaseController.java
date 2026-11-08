package com.smallbiz.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.purchasing.PurchaseDtos.CancelRequest;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseRequest;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseSummary;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseView;
import com.smallbiz.purchasing.PurchaseDtos.SuppliedProduct;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;

/** 采购：涉及成本和供应商，仅店主/管理员。 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class PurchaseController {

    private static final Map<String, String> SORTABLE = Map.of("orderDate", "orderDate", "totalAmount", "totalAmount",
            "orderNo", "orderNo");

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping("/purchase-orders")
    public PageResponse<PurchaseSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Long tenantId = CurrentUser.requireTenantId();
        return service.list(tenantId, new PurchaseService.Filter(q, status, supplierId, from, to),
                Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "orderDate", "id")));
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id);
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseView> create(@Valid @RequestBody PurchaseRequest req) {
        Long tenantId = CurrentUser.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, tenantId, CurrentUser.userId()));
    }

    @PutMapping("/purchase-orders/{id}")
    public PurchaseView update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        CurrentUser.requireTenantId();
        return service.update(id, req);
    }

    @PostMapping("/purchase-orders/{id}/submit")
    public PurchaseView submit(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.submit(id);
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public PurchaseView receive(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.receive(id, CurrentUser.userId());
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public PurchaseView cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req) {
        CurrentUser.requireTenantId();
        return service.cancel(id, req == null ? null : req.reason(), CurrentUser.userId());
    }

    @DeleteMapping("/purchase-orders/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 某供应商供应过的商品（来自已收货的采购单）。 */
    @GetMapping("/suppliers/{id}/products")
    public List<SuppliedProduct> suppliedProducts(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.suppliedProducts(id);
    }
}
