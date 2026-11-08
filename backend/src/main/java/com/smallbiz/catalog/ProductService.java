package com.smallbiz.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.auth.User;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.catalog.ProductDtos.CreateProductRequest;
import com.smallbiz.catalog.ProductDtos.MovementView;
import com.smallbiz.catalog.ProductDtos.ProductView;
import com.smallbiz.catalog.ProductDtos.UpdateProductRequest;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.inventory.InventoryService;
import com.smallbiz.inventory.MovementType;
import com.smallbiz.inventory.StockMovement;
import com.smallbiz.inventory.StockMovementRepository;
import com.smallbiz.tenant.SequenceService;

@Service
public class ProductService {

    /** 列表筛选条件。 */
    public record Filter(String q, Long categoryId, ProductStatus status, Boolean lowStock) {
    }

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final InventoryService inventory;
    private final StockMovementRepository movements;
    private final SequenceService sequences;
    private final ImageStorage images;
    private final UserRepository users;
    private final PlanGuard planGuard;

    public ProductService(ProductRepository products, CategoryRepository categories, CategoryService categoryService,
            InventoryService inventory, StockMovementRepository movements, SequenceService sequences,
            ImageStorage images, UserRepository users, PlanGuard planGuard) {
        this.planGuard = planGuard;
        this.products = products;
        this.categories = categories;
        this.categoryService = categoryService;
        this.inventory = inventory;
        this.movements = movements;
        this.sequences = sequences;
        this.images = images;
        this.users = users;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public PageResponse<ProductView> list(Filter filter, Pageable pageable, boolean hideCost) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (filter.q() != null && !filter.q().isBlank()) {
            String like = "%" + escapeLike(filter.q().trim().toLowerCase()) + "%";
            String exact = filter.q().trim();
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("sku")), like, '\\'),
                    cb.equal(root.get("barcode"), exact)));
        }
        if (filter.categoryId() != null) {
            List<Long> ids = categoryService.selfAndChildren(filter.categoryId());
            spec = spec.and((root, query, cb) -> root.get("categoryId").in(ids));
        }
        if (filter.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Boolean.TRUE.equals(filter.lowStock())) {
            spec = spec.and((root, query, cb) -> cb.le(root.get("stockQty"), root.get("lowStockThreshold")));
        }
        Page<Product> page = products.findAll(spec, pageable);
        Map<Long, String> categoryNames = categoryNames();
        return PageResponse.of(page, p -> toView(p, categoryNames, hideCost));
    }

    @Transactional(readOnly = true)
    public ProductView get(Long id, boolean hideCost) {
        return toView(load(id), categoryNames(), hideCost);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementView> movements(Long productId, Pageable pageable, boolean hideCost) {
        load(productId); // 校验商品属于当前店铺
        Page<StockMovement> page = movements.findByProductId(productId, pageable);
        Set<Long> userIds = new HashSet<>();
        page.forEach(m -> {
            if (m.getCreatedBy() != null) {
                userIds.add(m.getCreatedBy());
            }
        });
        Map<Long, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        return PageResponse.of(page, m -> new MovementView(m.getId(), m.getType(), m.getQtyChange(),
                m.getQtyBefore(), m.getQtyAfter(), hideCost ? null : m.getUnitCost(), m.getRefType(), m.getRefId(),
                m.getReason(), names.get(m.getCreatedBy()), m.getCreatedAt()));
    }

    // ---------------- 新增 / 修改 / 删除 ----------------

    @Transactional
    public ProductView create(CreateProductRequest req, Long tenantId, Long userId) {
        Integer max = PlanPolicy.maxProducts(planGuard.currentPlan(tenantId));
        if (max != null && products.count() >= max) {
            throw PlanGuard.limitReached("products", max);
        }
        String sku = blankToNull(req.sku());
        if (sku == null) {
            sku = nextAutoSku(tenantId);
        } else if (products.existsBySku(sku)) {
            throw ApiException.conflict("SKU_EXISTS", "SKU「" + sku + "」已被其他商品使用");
        }
        Product p = new Product(sku, req.name().trim(), userId);
        applyCommon(p, req.categoryId(), req.description(), req.unit(), req.barcode(), req.costPrice(),
                req.retailPrice(), req.wholesalePrice(), req.lowStockThreshold(),
                req.status() == null ? ProductStatus.ACTIVE : req.status());
        p = saveFlush(p);
        if (req.initialStock() > 0) {
            inventory.apply(p.getId(), new InventoryService.Change(MovementType.INITIAL, req.initialStock(),
                    req.costPrice(), null, null, "新建商品时的初始库存", userId));
        }
        return toView(p, categoryNames(), false);
    }

    @Transactional
    public ProductView update(Long id, UpdateProductRequest req) {
        Product p = load(id);
        if (p.getVersion() != req.version()) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION", "商品已被其他人修改，请刷新后重试");
        }
        String sku = req.sku().trim();
        if (!sku.equals(p.getSku())) {
            products.findBySku(sku).filter(other -> !other.getId().equals(p.getId())).ifPresent(other -> {
                throw ApiException.conflict("SKU_EXISTS", "SKU「" + sku + "」已被其他商品使用");
            });
            p.setSku(sku);
        }
        p.setName(req.name().trim());
        applyCommon(p, req.categoryId(), req.description(), req.unit(), req.barcode(), req.costPrice(),
                req.retailPrice(), req.wholesalePrice(), req.lowStockThreshold(), req.status());
        Product saved = saveFlush(p);
        return toView(saved, categoryNames(), false);
    }

    @Transactional
    public void delete(Long id) {
        Product p = load(id);
        p.softDelete(Instant.now());
    }

    @Transactional
    public ProductView uploadImage(Long id, Long tenantId, MultipartFile file) {
        Product p = load(id);
        String old = p.getImagePath();
        p.setImagePath(images.store(tenantId, file));
        products.flush();
        images.delete(old);
        return toView(p, categoryNames(), false);
    }

    @Transactional
    public ProductView deleteImage(Long id) {
        Product p = load(id);
        String old = p.getImagePath();
        p.setImagePath(null);
        products.flush();
        images.delete(old);
        return toView(p, categoryNames(), false);
    }

    // ---------------- 内部 ----------------

    /** 加载当前店铺的商品；其他店铺的商品或已删除商品一律视为不存在（404）。 */
    Product load(Long id) {
        return products.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
    }

    private void applyCommon(Product p, Long categoryId, String description, String unit, String barcode,
            BigDecimal costPrice, BigDecimal retailPrice, BigDecimal wholesalePrice, int lowStockThreshold,
            ProductStatus status) {
        if (categoryId != null) {
            categoryService.load(categoryId); // 分类必须属于当前店铺
        }
        p.setCategoryId(categoryId);
        p.setDescription(blankToNull(description));
        p.setUnit(unit.trim());
        p.setBarcode(blankToNull(barcode));
        p.setCostPrice(costPrice);
        p.setRetailPrice(retailPrice);
        p.setWholesalePrice(wholesalePrice);
        p.setLowStockThreshold(lowStockThreshold);
        p.setStatus(status);
    }

    private Product saveFlush(Product p) {
        try {
            return products.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // 并发情况下唯一索引兜底
            throw ApiException.conflict("SKU_EXISTS", "SKU「" + p.getSku() + "」已被其他商品使用");
        }
    }

    /** 自动编号：SKU000001、SKU000002……若被手工占用则顺延。 */
    private String nextAutoSku(Long tenantId) {
        for (int i = 0; i < 20; i++) {
            String candidate = String.format("SKU%06d", sequences.next(tenantId, "SKU"));
            if (!products.existsBySku(candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("SKU_GENERATION_FAILED", "自动生成 SKU 失败，请手动填写");
    }

    private Map<Long, String> categoryNames() {
        List<Category> all = categories.findAll();
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        return all.stream().collect(Collectors.toMap(Category::getId, c -> {
            List<String> parts = new ArrayList<>();
            if (c.getParentId() != null && byId.containsKey(c.getParentId())) {
                parts.add(byId.get(c.getParentId()).getName());
            }
            parts.add(c.getName());
            return String.join(" / ", parts);
        }));
    }

    private ProductView toView(Product p, Map<Long, String> categoryNames, boolean hideCost) {
        return new ProductView(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                ImageStorage.url(p.getImagePath()), p.getUnit(), p.getBarcode(), p.getCategoryId(),
                p.getCategoryId() == null ? null : categoryNames.get(p.getCategoryId()),
                hideCost ? null : p.getCostPrice(), p.getRetailPrice(), p.getWholesalePrice(), p.getStockQty(),
                p.getLowStockThreshold(), p.isLowStock(), p.getStatus(), p.getVersion(), p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
