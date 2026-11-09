package com.smallbiz.warehouse;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.warehouse.WarehouseDtos.StockLine;
import com.smallbiz.warehouse.WarehouseDtos.TransferRequest;
import com.smallbiz.warehouse.WarehouseDtos.TransferView;
import com.smallbiz.warehouse.WarehouseDtos.WarehouseList;
import com.smallbiz.warehouse.WarehouseDtos.WarehouseRequest;

import jakarta.validation.Valid;

/** 仓库与调拨。查看：所有成员（店员看不到库存价值）；维护与调拨：店主/管理员。 */
@RestController
@RequestMapping("/api/v1")
public class WarehouseController {

    private final WarehouseService warehouses;
    private final TransferService transfers;

    public WarehouseController(WarehouseService warehouses, TransferService transfers) {
        this.warehouses = warehouses;
        this.transfers = transfers;
    }

    @GetMapping("/warehouses")
    public WarehouseList list() {
        return warehouses.list(CurrentUser.requireTenantId(), CurrentUser.roleOrNull() == MemberRole.STAFF);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody WarehouseRequest req) {
        Warehouse w = warehouses.create(CurrentUser.requireTenantId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", w.getId()));
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Long> update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest req) {
        CurrentUser.requireTenantId();
        return Map.of("id", warehouses.update(id, req).getId());
    }

    @PostMapping("/warehouses/{id}/deactivate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        warehouses.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** 某仓库中这些商品的库存：[{productId, qty}]。 */
    @GetMapping("/warehouses/{id}/stocks")
    public List<Map<String, Object>> stocks(@PathVariable Long id, @RequestParam List<Long> productIds) {
        CurrentUser.requireTenantId();
        if (productIds.size() > 500) {
            throw ApiException.badRequest("TOO_MANY_PRODUCTS", "一次最多查询 500 个商品");
        }
        return warehouses.quantities(id, productIds).entrySet().stream()
                .map(e -> Map.<String, Object>of("productId", e.getKey(), "qty", e.getValue())).toList();
    }

    @GetMapping("/products/{productId}/stocks")
    public List<StockLine> productStocks(@PathVariable Long productId) {
        CurrentUser.requireTenantId();
        return warehouses.productStocks(productId);
    }

    @GetMapping("/stock-transfers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PageResponse<TransferView> transfers(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        CurrentUser.requireTenantId();
        return transfers.list(warehouseId, Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/stock-transfers/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public TransferView transfer(@PathVariable Long id) {
        CurrentUser.requireTenantId();
        return transfers.get(id);
    }

    @PostMapping("/stock-transfers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<TransferView> createTransfer(@Valid @RequestBody TransferRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transfers.create(CurrentUser.requireTenantId(), req, CurrentUser.userId()));
    }
}
