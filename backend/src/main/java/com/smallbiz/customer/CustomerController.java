package com.smallbiz.customer;

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
import com.smallbiz.customer.CustomerDtos.CustomerRequest;
import com.smallbiz.customer.CustomerDtos.CustomerView;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;

/** 客户：所有成员可查看和新增（店员开单时需要登记客户）；修改、删除限店主/管理员。 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private static final Map<String, String> SORTABLE = Map.of("name", "name", "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CustomerView> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Customer.Type type,
            @RequestParam(required = false) Customer.PriceTier priceTier,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser.requireTenantId();
        return service.list(new CustomerService.Filter(q, type, priceTier),
                Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
    }

    @GetMapping("/{id}")
    public CustomerView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<CustomerView> create(@Valid @RequestBody CustomerRequest req) {
        CurrentUser.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, CurrentUser.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CustomerView update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
