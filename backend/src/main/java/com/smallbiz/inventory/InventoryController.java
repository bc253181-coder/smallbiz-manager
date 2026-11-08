package com.smallbiz.inventory;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Paging;
import com.smallbiz.inventory.InventoryDtos.AdjustRequest;
import com.smallbiz.inventory.InventoryDtos.LowStockList;
import com.smallbiz.inventory.InventoryDtos.MovementRow;
import com.smallbiz.inventory.InventoryDtos.StockInRequest;
import com.smallbiz.inventory.InventoryDtos.StockOutRequest;
import com.smallbiz.inventory.InventoryDtos.StockResult;
import com.smallbiz.inventory.InventoryDtos.Summary;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.MemberRole;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final InventoryQueryService queries;
    private final ProductRepository products;

    public InventoryController(InventoryService inventory, InventoryQueryService queries, ProductRepository products) {
        this.inventory = inventory;
        this.queries = queries;
        this.products = products;
    }

    private static boolean hideCost() {
        return CurrentUser.roleOrNull() == MemberRole.STAFF;
    }

    // ---------------- 库存操作（店主/管理员） ----------------

    @PostMapping("/stock-in")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Transactional
    public ResponseEntity<StockResult> stockIn(@Valid @RequestBody StockInRequest req) {
        CurrentUser.requireTenantId();
        StockMovement m = inventory.apply(req.productId(), new InventoryService.Change(MovementType.STOCK_IN,
                req.quantity(), req.unitCost(), null, null, req.reason(), CurrentUser.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result(m));
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Transactional
    public ResponseEntity<StockResult> stockOut(@Valid @RequestBody StockOutRequest req) {
        CurrentUser.requireTenantId();
        StockMovement m = inventory.apply(req.productId(), new InventoryService.Change(MovementType.STOCK_OUT,
                -req.quantity(), null, null, null, req.reason(), CurrentUser.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result(m));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Transactional
    public ResponseEntity<StockResult> adjust(@Valid @RequestBody AdjustRequest req) {
        CurrentUser.requireTenantId();
        StockMovement m = inventory.adjustTo(req.productId(), req.actualQty(), req.reason(), CurrentUser.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result(m));
    }

    private StockResult result(StockMovement m) {
        var p = products.findById(m.getProductId()).orElseThrow();
        return new StockResult(m.getId(), p.getId(), m.getQtyChange(), p.getStockQty(),
                hideCost() ? null : p.getCostPrice());
    }

    // ---------------- 查询 ----------------

    @GetMapping("/movements")
    public PageResponse<MovementRow> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long tenantId = CurrentUser.requireTenantId();
        return queries.movements(tenantId, new InventoryQueryService.MovementFilter(productId, type, from, to),
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt", "id")), hideCost());
    }

    @GetMapping("/summary")
    public Summary summary() {
        CurrentUser.requireTenantId();
        return queries.summary(hideCost());
    }

    @GetMapping("/low-stock")
    public LowStockList lowStock(@RequestParam(defaultValue = "20") int limit) {
        CurrentUser.requireTenantId();
        return queries.lowStock(limit);
    }
}
