package com.smallbiz.excel;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.RequiresFeature;
import com.smallbiz.catalog.ProductService;
import com.smallbiz.catalog.ProductStatus;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.inventory.InventoryQueryService;
import com.smallbiz.inventory.MovementType;
import com.smallbiz.purchasing.PurchaseService;
import com.smallbiz.purchasing.PurchaseStatus;
import com.smallbiz.sales.SalesOrder;
import com.smallbiz.sales.SalesService;
import com.smallbiz.security.CurrentUser;

/** Excel 导出（专业版，仅店主/管理员）。筛选参数与对应列表接口一致。 */
@RestController
@RequestMapping("/api/v1/exports")
@RequiresFeature(Feature.EXCEL_EXPORT)
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ExportController {

    private final ExportService service;
    private final PlanGuard planGuard;

    public ExportController(ExportService service, PlanGuard planGuard) {
        this.service = service;
        this.planGuard = planGuard;
    }

    private String today(Long tenantId) {
        return LocalDate.now(service.zone(tenantId)).toString();
    }

    @GetMapping("/products")
    public ResponseEntity<byte[]> products(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Boolean lowStock) {
        Long tenantId = CurrentUser.requireTenantId();
        return XlsxWriter.response("商品_" + today(tenantId) + ".xlsx",
                service.products(tenantId, new ProductService.Filter(q, categoryId, status, lowStock)));
    }

    @GetMapping("/customers")
    public ResponseEntity<byte[]> customers(@RequestParam(required = false) String q,
            @RequestParam(required = false) Customer.Type type, @RequestParam(required = false) Customer.PriceTier priceTier) {
        Long tenantId = CurrentUser.requireTenantId();
        planGuard.require(tenantId, Feature.CUSTOMERS);
        return XlsxWriter.response("客户_" + today(tenantId) + ".xlsx",
                service.customers(tenantId, new CustomerService.Filter(q, type, priceTier)));
    }

    @GetMapping("/suppliers")
    public ResponseEntity<byte[]> suppliers(@RequestParam(required = false) String q) {
        Long tenantId = CurrentUser.requireTenantId();
        return XlsxWriter.response("供应商_" + today(tenantId) + ".xlsx", service.suppliers(tenantId, q));
    }

    @GetMapping("/sales")
    public ResponseEntity<byte[]> sales(@RequestParam(required = false) String q,
            @RequestParam(required = false) SalesOrder.Status status, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long createdBy) {
        Long tenantId = CurrentUser.requireTenantId();
        return XlsxWriter.response("销售_" + range(from, to, tenantId) + ".xlsx",
                service.sales(tenantId, new SalesService.Filter(q, status, customerId, from, to, createdBy)));
    }

    @GetMapping("/purchases")
    public ResponseEntity<byte[]> purchases(@RequestParam(required = false) String q,
            @RequestParam(required = false) PurchaseStatus status, @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long tenantId = CurrentUser.requireTenantId();
        return XlsxWriter.response("采购_" + range(from, to, tenantId) + ".xlsx",
                service.purchases(tenantId, new PurchaseService.Filter(q, status, supplierId, from, to)));
    }

    @GetMapping("/movements")
    public ResponseEntity<byte[]> movements(@RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long tenantId = CurrentUser.requireTenantId();
        return XlsxWriter.response("库存流水_" + range(from, to, tenantId) + ".xlsx",
                service.movements(tenantId, new InventoryQueryService.MovementFilter(productId, type, from, to)));
    }

    private String range(LocalDate from, LocalDate to, Long tenantId) {
        if (from == null && to == null) {
            return "全部_" + today(tenantId);
        }
        return (from == null ? "开始" : from.toString()) + "_" + (to == null ? today(tenantId) : to.toString());
    }
}
