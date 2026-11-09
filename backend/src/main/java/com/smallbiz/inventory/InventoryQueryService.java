package com.smallbiz.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.catalog.ProductStatus;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.inventory.InventoryDtos.LowStockItem;
import com.smallbiz.inventory.InventoryDtos.LowStockList;
import com.smallbiz.inventory.InventoryDtos.MovementRow;
import com.smallbiz.inventory.InventoryDtos.Summary;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

/** 库存查询：流水、汇总、低库存。 */
@Service
public class InventoryQueryService {

    public record MovementFilter(Long productId, MovementType type, LocalDate from, LocalDate to, Long warehouseId) {
        public MovementFilter(Long productId, MovementType type, LocalDate from, LocalDate to) {
            this(productId, type, from, to, null);
        }
    }

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final com.smallbiz.warehouse.WarehouseRepository warehouses;

    public InventoryQueryService(StockMovementRepository movements, ProductRepository products, UserRepository users,
            TenantRepository tenants, com.smallbiz.warehouse.WarehouseRepository warehouses) {
        this.warehouses = warehouses;
        this.movements = movements;
        this.products = products;
        this.users = users;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementRow> movements(Long tenantId, MovementFilter f, Pageable pageable, boolean hideCost) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        ZoneId zone = zone(tenantId);
        Specification<StockMovement> spec = (root, q, cb) -> cb.conjunction();
        if (f.productId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("productId"), f.productId()));
        }
        if (f.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), f.type()));
        }
        if (f.warehouseId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("warehouseId"), f.warehouseId()));
        }
        if (f.from() != null) {
            var start = f.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (f.to() != null) {
            var end = f.to().plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        Page<StockMovement> page = movements.findAll(spec, pageable);

        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        page.forEach(m -> {
            productIds.add(m.getProductId());
            if (m.getCreatedBy() != null) {
                userIds.add(m.getCreatedBy());
            }
        });
        // 已删除的商品查不到（软删除过滤），显示为"已删除商品"
        Map<Long, Product> productMap = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> userNames = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<Long, String> warehouseNames = warehouses.findAll().stream()
                .collect(Collectors.toMap(com.smallbiz.warehouse.Warehouse::getId, com.smallbiz.warehouse.Warehouse::getName));

        return PageResponse.of(page, m -> {
            Product p = productMap.get(m.getProductId());
            return new MovementRow(m.getId(), m.getProductId(), p == null ? "（已删除商品）" : p.getName(),
                    p == null ? null : p.getSku(), p == null ? null : p.getUnit(), m.getWarehouseId(),
                    warehouseNames.get(m.getWarehouseId()), m.getType(), m.getQtyChange(),
                    m.getQtyBefore(), m.getQtyAfter(), hideCost ? null : m.getUnitCost(), m.getRefType(),
                    m.getRefId(), m.getReason(), userNames.get(m.getCreatedBy()), m.getCreatedAt());
        });
    }

    @Transactional(readOnly = true)
    public Summary summary(boolean hideCost) {
        Object[] totals = products.stockTotals().get(0);
        Object[] alerts = products.alertCounts().get(0);
        BigDecimal value = toDecimal(totals[2]).setScale(2, java.math.RoundingMode.HALF_UP);
        return new Summary(toLong(totals[0]), toLong(totals[1]), hideCost ? null : value, toLong(alerts[0]),
                toLong(alerts[1]));
    }

    /** 在售商品中库存 ≤ 预警值的商品，最缺的排在前面。 */
    @Transactional(readOnly = true)
    public LowStockList lowStock(int limit) {
        Specification<Product> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), ProductStatus.ACTIVE),
                cb.le(root.get("stockQty"), root.get("lowStockThreshold")));
        Page<Product> page = products.findAll(spec,
                PageRequest.of(0, Math.max(1, Math.min(limit, 100)), Sort.by("stockQty").ascending().and(Sort.by("id"))));
        List<LowStockItem> items = page.getContent().stream()
                .map(p -> new LowStockItem(p.getId(), p.getSku(), p.getName(), p.getUnit(),
                        ImageStorage.url(p.getImagePath()), p.getStockQty(), p.getLowStockThreshold()))
                .toList();
        return new LowStockList(page.getTotalElements(), items);
    }

    private ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getTimezone).map(ZoneId::of).orElse(ZoneId.of("Asia/Shanghai"));
    }

    private static long toLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
