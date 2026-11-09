package com.smallbiz.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.catalog.ProductStatus;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Strings;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.inventory.InventoryService;
import com.smallbiz.inventory.MovementType;
import com.smallbiz.receivable.CustomerPayment;
import com.smallbiz.receivable.CustomerPaymentRepository;
import com.smallbiz.receivable.PaymentAllocation;
import com.smallbiz.receivable.PaymentAllocationRepository;
import com.smallbiz.sales.SalesDtos.CustomerStats;
import com.smallbiz.sales.SalesDtos.ItemRequest;
import com.smallbiz.sales.SalesDtos.ItemView;
import com.smallbiz.sales.SalesDtos.SaleRequest;
import com.smallbiz.sales.SalesDtos.SaleSummary;
import com.smallbiz.sales.SalesDtos.SaleView;
import com.smallbiz.tenant.SequenceService;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

@Service
public class SalesService {

    public static final String REF_TYPE = "SALES_ORDER";

    public record Filter(String q, SalesOrder.Status status, Long customerId, LocalDate from, LocalDate to) {
    }

    private final SalesOrderRepository orders;
    private final ProductRepository products;
    private final CustomerService customers;
    private final InventoryService inventory;
    private final SequenceService sequences;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final PlanGuard planGuard;
    private final PaymentAllocationRepository allocations;
    private final CustomerPaymentRepository payments;

    public SalesService(SalesOrderRepository orders, ProductRepository products, CustomerService customers,
            InventoryService inventory, SequenceService sequences, UserRepository users, TenantRepository tenants,
            PlanGuard planGuard, PaymentAllocationRepository allocations, CustomerPaymentRepository payments) {
        this.allocations = allocations;
        this.payments = payments;
        this.orders = orders;
        this.products = products;
        this.customers = customers;
        this.inventory = inventory;
        this.sequences = sequences;
        this.users = users;
        this.tenants = tenants;
        this.planGuard = planGuard;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> list(Long tenantId, Filter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        ZoneId zone = zone(tenantId);
        Specification<SalesOrder> spec = (root, q, cb) -> cb.conjunction();
        if (f.q() != null && !f.q().isBlank()) {
            String like = Strings.likeContains(f.q());
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("orderNo")), like, '\\'),
                    cb.like(cb.lower(root.get("customerName")), like, '\\')));
        }
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.customerId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customerId"), f.customerId()));
        }
        if (f.from() != null) {
            Instant start = f.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("orderDate"), start));
        }
        if (f.to() != null) {
            Instant end = f.to().plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("orderDate"), end));
        }
        return PageResponse.of(orders.findAll(spec, pageable), SaleSummary::of);
    }

    @Transactional(readOnly = true)
    public SaleView get(Long id, boolean hideCost) {
        return toView(load(id), hideCost);
    }

    @Transactional(readOnly = true)
    public CustomerStats customerStats(Long customerId) {
        customers.load(customerId);
        Object[] r = orders.customerStats(customerId).get(0);
        return new CustomerStats(((Number) r[0]).longValue(), (BigDecimal) r[1], (Instant) r[2]);
    }

    // ---------------- 开单 ----------------

    /**
     * 开单：校验商品与价格 → 保存订单 → 按商品 id 顺序扣减库存（行锁，不会超卖）。
     * 任一商品库存不足则整单回滚（包括单号），不会出现部分扣减。
     */
    @Transactional
    public SaleView create(SaleRequest req, Long tenantId, Long userId, boolean hideCost) {
        // 免费版：只能给散客按零售价开单
        if (req.customerId() != null) {
            planGuard.require(tenantId, Feature.CUSTOMERS);
        }
        if (req.priceTier() == Customer.PriceTier.WHOLESALE) {
            planGuard.require(tenantId, Feature.WHOLESALE_PRICE);
        }
        Customer customer = req.customerId() == null ? null : customers.load(req.customerId());
        List<SalesOrderItem> items = buildItems(req.items(), req.priceTier());
        BigDecimal subtotal = items.stream().map(SalesOrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = (req.discountAmount() == null ? BigDecimal.ZERO : req.discountAmount())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (discount.compareTo(subtotal) > 0) {
            throw ApiException.badRequest("DISCOUNT_TOO_LARGE", "优惠金额不能超过商品合计");
        }

        SalesOrder order = new SalesOrder(sequences.documentNo(tenantId, "SO"), Instant.now(), req.priceTier(), userId);
        if (customer != null) {
            order.setCustomer(customer.getId(), customer.getName());
        }
        order.setNotes(Strings.blankToNull(req.notes()));
        order.setItems(items, discount);
        applyPaidAtSale(order, req.paidAmount(), customer, tenantId);
        order = orders.saveAndFlush(order);

        for (SalesOrderItem item : sortedByProduct(order)) {
            inventory.apply(item.getProductId(), new InventoryService.Change(MovementType.SALE, -item.getQuantity(),
                    null, REF_TYPE, order.getId(), "销售 " + order.getOrderNo(), userId));
        }
        return toView(order, hideCost);
    }

    /** 取消：恢复库存并标记为已取消。已取消的订单不计入任何统计。 */
    @Transactional
    public SaleView cancel(Long id, String reason, Long userId) {
        SalesOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "销售单不存在"));
        if (order.getStatus() != SalesOrder.Status.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "该销售单已取消");
        }
        // 已收过款的单不能直接取消，否则钱和单对不上（先作废相关收款）
        if (allocations.existsValidForOrder(order.getId())) {
            throw ApiException.conflict("ORDER_HAS_PAYMENTS", "这张销售单已有收款记录，请先作废相关收款再取消");
        }
        for (SalesOrderItem item : sortedByProduct(order)) {
            try {
                inventory.apply(item.getProductId(), new InventoryService.Change(MovementType.SALE_CANCEL,
                        item.getQuantity(), null, REF_TYPE, order.getId(), "取消销售 " + order.getOrderNo(), userId));
            } catch (ApiException e) {
                if ("PRODUCT_NOT_FOUND".equals(e.getCode())) {
                    throw ApiException.conflict("PRODUCT_DELETED",
                            "商品「" + item.getProductName() + "」已被删除，无法恢复库存，因此不能取消该销售单。");
                }
                throw e;
            }
        }
        order.cancel(Instant.now(), userId, Strings.blankToNull(reason));
        return toView(orders.saveAndFlush(order), false);
    }

    // ---------------- 内部 ----------------

    /** 本次实收：为空 = 全额；少于应收即赊账，必须选择客户且为专业版。 */
    private void applyPaidAtSale(SalesOrder order, BigDecimal paidAmount, Customer customer, Long tenantId) {
        if (paidAmount == null) {
            return;
        }
        BigDecimal paid = paidAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (paid.compareTo(order.getTotalAmount()) > 0) {
            throw ApiException.badRequest("PAID_EXCEEDS_TOTAL", "实收金额不能超过应收金额");
        }
        if (paid.compareTo(order.getTotalAmount()) < 0) {
            if (customer == null) {
                throw ApiException.badRequest("CREDIT_REQUIRES_CUSTOMER", "赊账需要先选择客户；散客请全额收款");
            }
            planGuard.require(tenantId, Feature.RECEIVABLES);
        }
        order.setPaidAtSale(paid);
    }

    private List<SalesOrderItem> buildItems(List<ItemRequest> reqs, Customer.PriceTier tier) {
        Set<Long> seen = new HashSet<>();
        List<SalesOrderItem> items = new ArrayList<>();
        for (ItemRequest r : reqs) {
            if (!seen.add(r.productId())) {
                throw ApiException.badRequest("DUPLICATE_PRODUCT", "同一个商品不能重复添加，请合并数量");
            }
            Product p = products.findById(r.productId())
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在或已删除"));
            if (p.getStatus() != ProductStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_INACTIVE", "「" + p.getName() + "」已下架，不能销售",
                        Map.of("productId", p.getId()));
            }
            BigDecimal price = r.unitPrice() != null ? r.unitPrice() : defaultPrice(p, tier);
            items.add(new SalesOrderItem(p.getId(), p.getName(), p.getSku(), p.getUnit(), r.quantity(), price,
                    p.getCostPrice()));
        }
        return items;
    }

    /** 批发价客户使用批发价；商品未设批发价时按零售价。 */
    static BigDecimal defaultPrice(Product p, Customer.PriceTier tier) {
        if (tier == Customer.PriceTier.WHOLESALE && p.getWholesalePrice() != null) {
            return p.getWholesalePrice();
        }
        return p.getRetailPrice();
    }

    private static List<SalesOrderItem> sortedByProduct(SalesOrder order) {
        return order.getItems().stream().sorted(Comparator.comparing(SalesOrderItem::getProductId)).toList();
    }

    private SalesOrder load(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "销售单不存在"));
    }

    private ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getTimezone).map(ZoneId::of).orElse(ZoneId.of("Asia/Shanghai"));
    }

    private SaleView toView(SalesOrder o, boolean hideCost) {
        Set<Long> ids = Stream.of(o.getCreatedBy(), o.getCancelledBy()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        List<ItemView> items = o.getItems().stream()
                .map(i -> new ItemView(i.getProductId(), i.getProductName(), i.getSku(), i.getUnit(), i.getQuantity(),
                        i.getUnitPrice(), hideCost ? null : i.getUnitCost(), i.getLineTotal()))
                .toList();
        List<PaymentAllocation> allocs = o.getId() == null ? List.of() : allocations.findBySalesOrderIdOrderByIdAsc(o.getId());
        Map<Long, CustomerPayment> paymentById = payments.findAllById(allocs.stream().map(PaymentAllocation::getPaymentId).toList())
                .stream().collect(Collectors.toMap(CustomerPayment::getId, p -> p));
        List<SalesDtos.PaymentLine> paymentLines = allocs.stream().map(a -> {
            CustomerPayment p = paymentById.get(a.getPaymentId());
            return new SalesDtos.PaymentLine(p.getId(), p.getPaymentNo(), p.getReceivedAt(), p.getMethod().name(),
                    p.getStatus().name(), a.getAmount());
        }).toList();
        return new SaleView(o.getId(), o.getOrderNo(), o.getCustomerId(), o.getCustomerName(), o.getPriceTier(),
                o.getStatus(), o.getOrderDate(), o.getItemCount(), o.getTotalQuantity(), o.getSubtotal(),
                o.getDiscountAmount(), o.getTotalAmount(), o.getPaidAmount(), o.getPaymentStatus(), o.getPaidAtSale(),
                o.getStatus() == SalesOrder.Status.COMPLETED ? o.outstanding() : BigDecimal.ZERO, o.getLastPaymentAt(),
                paymentLines,
                hideCost ? null : o.getTotalCost(), hideCost ? null : o.grossProfit(), o.getNotes(), items,
                names.get(o.getCreatedBy()), o.getCancelledAt(), names.get(o.getCancelledBy()), o.getCancelReason());
    }
}
