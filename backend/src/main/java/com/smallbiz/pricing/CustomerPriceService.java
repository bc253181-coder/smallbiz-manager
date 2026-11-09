package com.smallbiz.pricing;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.Strings;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerRepository;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.pricing.CustomerPriceDtos.CustomerPriceView;
import com.smallbiz.pricing.CustomerPriceDtos.ProductCustomerPriceView;
import com.smallbiz.pricing.CustomerPriceDtos.Quote;

/**
 * 客户协议价（专业版）。开单取价顺序：协议价 → 客户默认价格（零售 / 批发）→ 零售价。
 * 套餐到期后数据保留，但开单不再使用协议价。
 */
@Service
public class CustomerPriceService {

    private final CustomerPriceRepository prices;
    private final CustomerService customerService;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PlanGuard planGuard;

    public CustomerPriceService(CustomerPriceRepository prices, CustomerService customerService,
            CustomerRepository customers, ProductRepository products, PlanGuard planGuard) {
        this.prices = prices;
        this.customerService = customerService;
        this.customers = customers;
        this.products = products;
        this.planGuard = planGuard;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<CustomerPriceView> list(Long customerId, String q) {
        customerService.load(customerId);
        List<CustomerPrice> rows = prices.findByCustomerIdOrderByIdDesc(customerId);
        // 已删除的商品查不到（软删除过滤）→ 协议价随之失效、不显示
        Map<Long, Product> byId = products.findAllById(rows.stream().map(CustomerPrice::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase();
        return rows.stream().filter(r -> byId.containsKey(r.getProductId())).map(r -> {
            Product p = byId.get(r.getProductId());
            return new CustomerPriceView(p.getId(), p.getName(), p.getSku(), p.getBarcode(), p.getUnit(),
                    p.getRetailPrice(), p.getWholesalePrice(), p.getCostPrice(), r.getPrice(), r.getNotes(),
                    r.getPrice().compareTo(p.getCostPrice()) < 0, r.getUpdatedAt());
        }).filter(v -> needle == null || v.productName().toLowerCase().contains(needle)
                || v.sku().toLowerCase().contains(needle) || needle.equals(v.barcode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductCustomerPriceView> byProduct(Long productId) {
        Product p = loadProduct(productId);
        List<CustomerPrice> rows = prices.findByProductIdOrderByIdDesc(productId);
        Map<Long, Customer> byId = customers.findAllById(rows.stream().map(CustomerPrice::getCustomerId).toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return rows.stream().filter(r -> byId.containsKey(r.getCustomerId())).map(r -> {
            Customer c = byId.get(r.getCustomerId());
            return new ProductCustomerPriceView(c.getId(), c.getName(), c.getPhone(), r.getPrice(), r.getNotes(),
                    r.getPrice().compareTo(p.getCostPrice()) < 0, r.getUpdatedAt());
        }).toList();
    }

    /** 开单取价：店铺不是专业版时返回空（开单回到零售 / 批发价）。 */
    @Transactional(readOnly = true)
    public List<Quote> quote(Long tenantId, Long customerId, Collection<Long> productIds) {
        customerService.load(customerId);
        return contractPrices(tenantId, customerId, productIds).entrySet().stream()
                .map(e -> new Quote(e.getKey(), e.getValue())).toList();
    }

    /** 供开单使用：productId → 协议价。没有客户、套餐不含协议价时为空。 */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Map<Long, BigDecimal> contractPrices(Long tenantId, Long customerId, Collection<Long> productIds) {
        if (customerId == null || productIds.isEmpty()
                || !PlanPolicy.allows(planGuard.currentPlan(tenantId), Feature.CUSTOMER_PRICES)) {
            return Map.of();
        }
        return prices.findByCustomerIdAndProductIdIn(customerId, productIds).stream()
                .collect(Collectors.toMap(CustomerPrice::getProductId, CustomerPrice::getPrice));
    }

    // ---------------- 修改 ----------------

    @Transactional
    public CustomerPriceView set(Long tenantId, Long customerId, Long productId, BigDecimal price, String notes, Long userId) {
        planGuard.require(tenantId, Feature.CUSTOMER_PRICES);
        customerService.load(customerId);
        Product p = loadProduct(productId);
        BigDecimal value = price.setScale(2, java.math.RoundingMode.HALF_UP);
        CustomerPrice row = prices.findForUpdate(customerId, productId).orElse(null);
        if (row == null) {
            row = new CustomerPrice(customerId, productId, userId);
            row.set(value, Strings.blankToNull(notes), userId);
            try {
                row = prices.saveAndFlush(row);
            } catch (DataIntegrityViolationException e) {
                // 两人同时为同一客户 + 商品新增：唯一约束兜底，提示刷新
                throw ApiException.conflict("CONCURRENT_MODIFICATION", "协议价刚被其他人设置，请刷新后重试");
            }
        } else {
            row.set(value, Strings.blankToNull(notes), userId);
            row = prices.saveAndFlush(row);
        }
        return new CustomerPriceView(p.getId(), p.getName(), p.getSku(), p.getBarcode(), p.getUnit(), p.getRetailPrice(),
                p.getWholesalePrice(), p.getCostPrice(), row.getPrice(), row.getNotes(),
                row.getPrice().compareTo(p.getCostPrice()) < 0, row.getUpdatedAt());
    }

    @Transactional
    public void delete(Long tenantId, Long customerId, Long productId) {
        planGuard.require(tenantId, Feature.CUSTOMER_PRICES);
        customerService.load(customerId);
        CustomerPrice row = prices.findByCustomerIdAndProductId(customerId, productId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_PRICE_NOT_FOUND", "这个商品没有设置协议价"));
        prices.delete(row);
    }

    private Product loadProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
    }
}
