package com.smallbiz.catalog;

import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.catalog.ProductDtos.CreateProductRequest;
import com.smallbiz.catalog.ProductDtos.MovementView;
import com.smallbiz.catalog.ProductDtos.ProductView;
import com.smallbiz.catalog.ProductDtos.UpdateProductRequest;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "sku", "sku",
            "retailPrice", "retailPrice",
            "stockQty", "stockQty",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    private static boolean hideCost() {
        return CurrentUser.roleOrNull() == MemberRole.STAFF;
    }

    @GetMapping
    public PageResponse<ProductView> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser.requireTenantId();
        return service.list(new ProductService.Filter(q, categoryId, status, lowStock),
                Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "updatedAt", "id")), hideCost());
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return service.get(id, hideCost());
    }

    @GetMapping("/{id}/movements")
    public PageResponse<MovementView> movements(@PathVariable Long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        CurrentUser.requireTenantId();
        return service.movements(id, Paging.of(page, size, null, Map.of(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")), hideCost());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ProductView> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, CurrentUser.requireTenantId(), CurrentUser.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ProductView uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.uploadImage(id, CurrentUser.requireTenantId(), file);
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ProductView deleteImage(@PathVariable Long id) {
        return service.deleteImage(id);
    }
}
