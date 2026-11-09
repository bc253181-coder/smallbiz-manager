package com.smallbiz.pricing;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.RequiresFeature;
import com.smallbiz.pricing.CustomerPriceDtos.CustomerPriceView;
import com.smallbiz.pricing.CustomerPriceDtos.PriceRequest;
import com.smallbiz.pricing.CustomerPriceDtos.ProductCustomerPriceView;
import com.smallbiz.pricing.CustomerPriceDtos.Quote;
import com.smallbiz.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * 客户协议价。查看列表、修改：店主/管理员（到期后列表仍可查看，修改需要专业版）；
 * 开单取价：所有成员（不是专业版时返回空）。
 */
@RestController
@RequestMapping("/api/v1")
public class CustomerPriceController {

    private final CustomerPriceService service;

    public CustomerPriceController(CustomerPriceService service) {
        this.service = service;
    }

    @GetMapping("/customers/{customerId}/prices")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @RequiresFeature(Feature.CUSTOMERS)
    public List<CustomerPriceView> list(@PathVariable Long customerId, @RequestParam(required = false) String q) {
        CurrentUser.requireTenantId();
        return service.list(customerId, q);
    }

    @PutMapping("/customers/{customerId}/prices/{productId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CustomerPriceView set(@PathVariable Long customerId, @PathVariable Long productId,
            @Valid @RequestBody PriceRequest req) {
        return service.set(CurrentUser.requireTenantId(), customerId, productId, req.price(), req.notes(),
                CurrentUser.userId());
    }

    @DeleteMapping("/customers/{customerId}/prices/{productId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long customerId, @PathVariable Long productId) {
        service.delete(CurrentUser.requireTenantId(), customerId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers/{customerId}/prices/quote")
    @RequiresFeature(Feature.CUSTOMERS)
    public List<Quote> quote(@PathVariable Long customerId, @RequestParam List<Long> productIds) {
        if (productIds.size() > 500) {
            throw com.smallbiz.common.ApiException.badRequest("TOO_MANY_PRODUCTS", "一次最多查询 500 个商品");
        }
        return service.quote(CurrentUser.requireTenantId(), customerId, productIds);
    }

    @GetMapping("/products/{productId}/customer-prices")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @RequiresFeature(Feature.CUSTOMERS)
    public List<ProductCustomerPriceView> byProduct(@PathVariable Long productId) {
        CurrentUser.requireTenantId();
        return service.byProduct(productId);
    }
}
