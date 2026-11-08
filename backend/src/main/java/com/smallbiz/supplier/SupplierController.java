package com.smallbiz.supplier;

import java.util.Map;

import org.springframework.data.domain.Sort;
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
import com.smallbiz.security.CurrentUser;
import com.smallbiz.supplier.SupplierDtos.SupplierRequest;
import com.smallbiz.supplier.SupplierDtos.SupplierView;

import jakarta.validation.Valid;

/** 供应商：涉及进货渠道和价格，仅店主/管理员可查看和管理。 */
@RestController
@RequestMapping("/api/v1/suppliers")
@com.smallbiz.billing.RequiresFeature(com.smallbiz.billing.Feature.SUPPLIERS)
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class SupplierController {

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SupplierView> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser.requireTenantId();
        return service.list(q, Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
    }

    @GetMapping("/{id}")
    public SupplierView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierView> create(@Valid @RequestBody SupplierRequest req) {
        CurrentUser.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, CurrentUser.userId()));
    }

    @PutMapping("/{id}")
    public SupplierView update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
