package com.smallbiz.purchasing;

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
import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Strings;
import com.smallbiz.inventory.InventoryService;
import com.smallbiz.inventory.MovementType;
import com.smallbiz.purchasing.PurchaseDtos.ItemRequest;
import com.smallbiz.purchasing.PurchaseDtos.ItemView;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseRequest;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseSummary;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseView;
import com.smallbiz.purchasing.PurchaseDtos.SuppliedProduct;
import com.smallbiz.supplier.Supplier;
import com.smallbiz.supplier.SupplierService;
import com.smallbiz.tenant.SequenceService;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

@Service
public class PurchaseService {

    public static final String REF_TYPE = "PURCHASE_ORDER";

    public record Filter(String q, PurchaseStatus status, Long supplierId, LocalDate from, LocalDate to) {
    }

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final SupplierService suppliers;
    private final InventoryService inventory;
    private final SequenceService sequences;
    private final UserRepository users;
    private final TenantRepository tenants;

    public PurchaseService(PurchaseOrderRepository orders, ProductRepository products, SupplierService suppliers,
            InventoryService inventory, SequenceService sequences, UserRepository users, TenantRepository tenants) {
        this.orders = orders;
        this.products = products;
        this.suppliers = suppliers;
        this.inventory = inventory;
        this.sequences = sequences;
        this.users = users;
        this.tenants = tenants;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummary> list(Long tenantId, Filter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        ZoneId zone = zone(tenantId);
        Specification<PurchaseOrder> spec = (root, q, cb) -> cb.conjunction();
        if (f.q() != null && !f.q().isBlank()) {
            String like = Strings.likeContains(f.q());
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("orderNo")), like, '\\'),
                    cb.like(cb.lower(root.get("supplierName")), like, '\\')));
        }
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.supplierId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("supplierId"), f.supplierId()));
        }
        if (f.from() != null) {
            Instant start = f.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("orderDate"), start));
        }
        if (f.to() != null) {
            Instant end = f.to().plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("orderDate"), end));
        }
        return PageResponse.of(orders.findAll(spec, pageable), PurchaseSummary::of);
    }

    @Transactional(readOnly = true)
    public PurchaseView get(Long id) {
        return toView(load(id));
    }

    @Transactional(readOnly = true)
    public List<SuppliedProduct> suppliedProducts(Long supplierId) {
        suppliers.load(supplierId);
        return orders.suppliedProducts(supplierId).stream()
                .map(r -> new SuppliedProduct((Long) r[0], (String) r[1], (String) r[2], (String) r[3],
                        ((Number) r[4]).longValue(), (Instant) r[5], ((Number) r[6]).longValue()))
                .toList();
    }

    // ---------------- 新增 / 修改 ----------------

    @Transactional
    public PurchaseView create(PurchaseRequest req, Long tenantId, Long userId) {
        Supplier supplier = suppliers.load(req.supplierId());
        List<PurchaseOrderItem> items = buildItems(req.items());
        Instant now = Instant.now();
        PurchaseOrder order = new PurchaseOrder(sequences.documentNo(tenantId, "PO"), now, userId);
        order.setSupplier(supplier.getId(), supplier.getName());
        order.setExpectedDate(req.expectedDate());
        order.setNotes(Strings.blankToNull(req.notes()));
        order.replaceItems(items);
        if (req.submit()) {
            order.submit(now);
        }
        return toView(orders.saveAndFlush(order));
    }

    @Transactional
    public PurchaseView update(Long id, PurchaseRequest req) {
        PurchaseOrder order = lock(id);
        if (!order.getStatus().isEditable()) {
            throw notAllowed("只有草稿或已下单的采购单可以修改");
        }
        if (req.version() == null || req.version() != order.getVersion()) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION", "采购单已被其他人修改，请刷新后重试");
        }
        Supplier supplier = suppliers.load(req.supplierId());
        order.setSupplier(supplier.getId(), supplier.getName());
        order.setExpectedDate(req.expectedDate());
        order.setNotes(Strings.blankToNull(req.notes()));
        order.replaceItems(buildItems(req.items()));
        if (req.submit() && order.getStatus() == PurchaseStatus.DRAFT) {
            order.submit(Instant.now());
        }
        return toView(orders.saveAndFlush(order));
    }

    // ---------------- 状态流转 ----------------

    @Transactional
    public PurchaseView submit(Long id) {
        PurchaseOrder order = lock(id);
        if (order.getStatus() != PurchaseStatus.DRAFT) {
            throw notAllowed("只有草稿可以提交下单");
        }
        order.submit(Instant.now());
        return toView(orders.saveAndFlush(order));
    }

    /**
     * 确认收货：所有商品入库（按商品 id 顺序加锁，避免死锁），并按移动加权平均更新成本。
     * 采购单行锁保证同一张单只能收货一次。
     */
    @Transactional
    public PurchaseView receive(Long id, Long userId) {
        PurchaseOrder order = lock(id);
        if (order.getStatus() == PurchaseStatus.RECEIVED) {
            throw notAllowed("该采购单已经收过货了");
        }
        if (order.getStatus() != PurchaseStatus.ORDERED) {
            throw notAllowed("只有已下单的采购单可以确认收货");
        }
        List<PurchaseOrderItem> sorted = order.getItems().stream()
                .sorted(Comparator.comparing(PurchaseOrderItem::getProductId)).toList();
        for (PurchaseOrderItem item : sorted) {
            try {
                inventory.apply(item.getProductId(), new InventoryService.Change(MovementType.PURCHASE_RECEIVE,
                        item.getQuantity(), item.getUnitCost(), REF_TYPE, order.getId(),
                        "采购收货 " + order.getOrderNo(), userId));
            } catch (ApiException e) {
                if ("PRODUCT_NOT_FOUND".equals(e.getCode())) {
                    throw ApiException.conflict("PRODUCT_DELETED",
                            "商品「" + item.getProductName() + "」已被删除，无法收货。请先修改采购单移除该商品。");
                }
                throw e;
            }
        }
        order.markReceived(Instant.now(), userId);
        return toView(orders.saveAndFlush(order));
    }

    @Transactional
    public PurchaseView cancel(Long id, String reason, Long userId) {
        PurchaseOrder order = lock(id);
        if (!order.getStatus().isEditable()) {
            throw notAllowed(order.getStatus() == PurchaseStatus.RECEIVED
                    ? "已收货的采购单不能取消。如需退货，请使用出库功能。"
                    : "该采购单已取消");
        }
        order.cancel(Instant.now(), userId, Strings.blankToNull(reason));
        return toView(orders.saveAndFlush(order));
    }

    @Transactional
    public void delete(Long id) {
        PurchaseOrder order = lock(id);
        if (order.getStatus() != PurchaseStatus.DRAFT) {
            throw notAllowed("只有草稿可以删除；已下单的采购单请使用取消");
        }
        orders.delete(order);
    }

    // ---------------- 内部 ----------------

    private List<PurchaseOrderItem> buildItems(List<ItemRequest> reqs) {
        Set<Long> seen = new HashSet<>();
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (ItemRequest r : reqs) {
            if (!seen.add(r.productId())) {
                throw ApiException.badRequest("DUPLICATE_PRODUCT", "同一个商品不能重复添加，请合并数量");
            }
            Product p = products.findById(r.productId())
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在或已删除"));
            items.add(new PurchaseOrderItem(p.getId(), p.getName(), p.getSku(), p.getUnit(), r.quantity(),
                    r.unitCost()));
        }
        return items;
    }

    private PurchaseOrder load(Long id) {
        return orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("PURCHASE_NOT_FOUND", "采购单不存在"));
    }

    private PurchaseOrder lock(Long id) {
        return orders.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("PURCHASE_NOT_FOUND", "采购单不存在"));
    }

    private static ApiException notAllowed(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", message);
    }

    private ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getTimezone).map(ZoneId::of).orElse(ZoneId.of("Asia/Shanghai"));
    }

    private PurchaseView toView(PurchaseOrder o) {
        Set<Long> userIds = Stream.of(o.getCreatedBy(), o.getReceivedBy(), o.getCancelledBy())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        List<ItemView> items = o.getItems().stream()
                .map(i -> new ItemView(i.getId(), i.getProductId(), i.getProductName(), i.getSku(), i.getUnit(),
                        i.getQuantity(), i.getReceivedQty(), i.getUnitCost(), i.getLineTotal()))
                .toList();
        return new PurchaseView(o.getId(), o.getOrderNo(), o.getSupplierId(), o.getSupplierName(), o.getStatus(),
                o.getOrderDate(), o.getExpectedDate(), o.getItemCount(), o.getTotalQuantity(), o.getTotalAmount(),
                o.getNotes(), items, names.get(o.getCreatedBy()), o.getSubmittedAt(), o.getReceivedAt(),
                names.get(o.getReceivedBy()), o.getCancelledAt(), names.get(o.getCancelledBy()), o.getCancelReason(),
                o.getVersion());
    }
}
