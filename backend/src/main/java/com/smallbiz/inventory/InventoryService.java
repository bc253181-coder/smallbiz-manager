package com.smallbiz.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

import jakarta.persistence.EntityManager;

/**
 * 库存的唯一修改入口。
 *
 * <p>每次调用在调用方的事务中：
 * <ol>
 *   <li>对商品行加写锁（SELECT … FOR UPDATE），同一商品的所有变动（不论哪个仓库）排队执行，不会超卖；</li>
 *   <li>按<b>仓库</b>校验库存是否足够（除非店铺允许负库存；调拨调出始终不允许变成负数）；</li>
 *   <li>更新该仓库库存和商品总库存，并写入一条流水（记录仓库，变动前后为该仓库的数量）。</li>
 * </ol>
 * 不变量：商品总库存 == 各仓库库存之和；某仓库库存 == 该仓库该商品所有流水 qty_change 之和。
 * 不指定仓库时使用店铺的默认仓库。
 *
 * <p>多个商品一起变动时（例如一张订单），调用方应按商品 id 升序调用，避免死锁。
 */
@Service
public class InventoryService {

    /** warehouseId 为空表示默认仓库。 */
    public record Change(MovementType type, int qtyChange, BigDecimal unitCost, String refType, Long refId,
            String reason, Long operatorId, Long warehouseId) {

        public Change(MovementType type, int qtyChange, BigDecimal unitCost, String refType, Long refId,
                String reason, Long operatorId) {
            this(type, qtyChange, unitCost, refType, refId, reason, operatorId, null);
        }
    }

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final TenantRepository tenants;
    private final EntityManager em;
    private final com.smallbiz.warehouse.DefaultWarehouse defaultWarehouse;
    private final com.smallbiz.warehouse.WarehouseRepository warehouses;

    public InventoryService(ProductRepository products, StockMovementRepository movements, TenantRepository tenants,
            EntityManager em, com.smallbiz.warehouse.DefaultWarehouse defaultWarehouse,
            com.smallbiz.warehouse.WarehouseRepository warehouses) {
        this.defaultWarehouse = defaultWarehouse;
        this.warehouses = warehouses;
        this.products = products;
        this.movements = movements;
        this.tenants = tenants;
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement apply(Long productId, Change change) {
        if (change.qtyChange() == 0) {
            throw ApiException.badRequest("ZERO_QUANTITY", "变动数量不能为 0");
        }
        Product product = lockFresh(productId);
        Long warehouseId = warehouseOf(change.warehouseId(), product.getTenantId());
        int before = product.getStockQty();
        long after = (long) before + change.qtyChange();
        int warehouseBefore = lockWarehouseQty(product.getId(), warehouseId);
        long warehouseAfter = (long) warehouseBefore + change.qtyChange();
        if (after > Integer.MAX_VALUE || after < Integer.MIN_VALUE
                || warehouseAfter > Integer.MAX_VALUE || warehouseAfter < Integer.MIN_VALUE) {
            throw ApiException.badRequest("QUANTITY_OUT_OF_RANGE", "库存数量超出范围");
        }
        if (warehouseAfter < 0 && change.qtyChange() < 0
                && (change.type() == MovementType.TRANSFER_OUT || !allowNegativeStock(product.getTenantId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "「" + product.getName() + "」库存不足",
                    Map.of("productId", product.getId(), "warehouseId", warehouseId, "available", warehouseBefore,
                            "requested", -change.qtyChange()));
        }
        // 成本按商品计算（各仓库共用移动加权平均成本），以总库存为基数
        if (change.qtyChange() > 0 && change.unitCost() != null
                && (change.type() == MovementType.STOCK_IN || change.type() == MovementType.PURCHASE_RECEIVE)) {
            product.setCostPrice(weightedAverageCost(before, product.getCostPrice(), change.qtyChange(),
                    change.unitCost()));
        }
        product.applyStockChange((int) after);
        writeWarehouseQty(product.getTenantId(), product.getId(), warehouseId, (int) warehouseAfter);
        return movements.save(new StockMovement(product.getId(), warehouseId, change.type(), change.qtyChange(),
                warehouseBefore, change.unitCost(), change.refType(), change.refId(), trimToNull(change.reason()),
                change.operatorId()));
    }

    /**
     * 盘点：把库存调整为实际清点的数量，差额记为一条 ADJUSTMENT 流水。
     * 先加锁再计算差额，避免并发时按过期的库存计算。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement adjustTo(Long productId, Long warehouseIdOrNull, int actualQty, String reason, Long operatorId) {
        if (actualQty < 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "实际数量不能为负数");
        }
        Product product = lockFresh(productId);
        Long warehouseId = warehouseOf(warehouseIdOrNull, product.getTenantId());
        int delta = actualQty - lockWarehouseQty(product.getId(), warehouseId);
        if (delta == 0) {
            throw ApiException.badRequest("NO_CHANGE", "实际数量与当前库存相同，无需调整");
        }
        return apply(productId, new Change(MovementType.ADJUSTMENT, delta, null, null, null, reason, operatorId,
                warehouseId));
    }

    /** 盘点默认仓库（兼容只有一个仓库的调用）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement adjustTo(Long productId, int actualQty, String reason, Long operatorId) {
        return adjustTo(productId, null, actualQty, reason, operatorId);
    }

    private Long warehouseOf(Long warehouseId, Long tenantId) {
        if (warehouseId == null) {
            return defaultWarehouse.id(tenantId);
        }
        // 仓库必须属于当前店铺（按店铺自动过滤）
        if (!warehouses.existsById(warehouseId)) {
            throw ApiException.notFound("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        return warehouseId;
    }

    /**
     * 加锁读取该仓库的最新库存（调用前商品行已加锁，同一商品的仓库库存不会并发修改）。
     *
     * <p>先确保这一行存在再加锁：对<b>不存在</b>的行 SELECT ... FOR UPDATE 会锁住唯一索引的间隙，
     * 多家店同时新建商品时（新商品的行都落在同一个间隙）会互相等待插入而死锁。
     * INSERT IGNORE 插入新行只需要插入意向锁（彼此不冲突）；行已存在时什么也不做，之后的 FOR UPDATE 只锁这一行。
     */
    private int lockWarehouseQty(Long productId, Long warehouseId) {
        em.createNativeQuery("""
                INSERT IGNORE INTO product_stocks (tenant_id, product_id, warehouse_id, qty, created_at, updated_at)
                SELECT p.tenant_id, p.id, :w, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM products p WHERE p.id = :p
                """).setParameter("p", productId).setParameter("w", warehouseId).executeUpdate();
        List<?> rows = em.createNativeQuery(
                "SELECT qty FROM product_stocks WHERE product_id = :p AND warehouse_id = :w FOR UPDATE")
                .setParameter("p", productId).setParameter("w", warehouseId).getResultList();
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)).intValue();
    }

    private void writeWarehouseQty(Long tenantId, Long productId, Long warehouseId, int qty) {
        em.createNativeQuery("""
                INSERT INTO product_stocks (tenant_id, product_id, warehouse_id, qty, created_at, updated_at)
                VALUES (:t, :p, :w, :q, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE qty = :q, updated_at = UTC_TIMESTAMP(6)
                """).setParameter("t", tenantId).setParameter("p", productId).setParameter("w", warehouseId)
                .setParameter("q", qty).executeUpdate();
    }

    /**
     * 移动加权平均成本：(现库存 × 原成本 + 入库数 × 入库单价) ÷ (现库存 + 入库数)。
     * 现库存 ≤ 0 时（没有可参考的存货）直接使用入库单价。
     */
    static BigDecimal weightedAverageCost(int currentQty, BigDecimal currentCost, int inQty, BigDecimal inCost) {
        if (currentQty <= 0 || currentCost == null) {
            return inCost.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = currentCost.multiply(BigDecimal.valueOf(currentQty))
                .add(inCost.multiply(BigDecimal.valueOf(inQty)));
        return total.divide(BigDecimal.valueOf((long) currentQty + inQty), 2, RoundingMode.HALF_UP);
    }

    /**
     * 加写锁并读取商品库存和成本的<b>最新已提交值</b>。
     *
     * <p>为什么不能只用 {@code findByIdForUpdate}：
     * <ol>
     *   <li>如果同一事务里之前读过这个商品（例如开单时先读价格），Hibernate 会返回持久化上下文中的
     *       旧对象，不会用 FOR UPDATE 查到的新数据覆盖字段；</li>
     *   <li>MySQL 默认 REPEATABLE READ：普通 SELECT 读的是事务第一次读取时的快照，
     *       Hibernate 的 refresh(PESSIMISTIC_WRITE) 也是用普通 SELECT 取数据，同样读到旧快照。</li>
     * </ol>
     * 只有"加锁读"（SELECT … FOR UPDATE）一定读到最新已提交的值。
     * 因此这里先用 JPQL 查询确认商品存在且属于当前店铺（并加锁），
     * 再用原生加锁读取得最新的库存和成本写回对象。并发开单测试覆盖了这个问题。
     */
    private Product lockFresh(Long productId) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT stock_qty, cost_price FROM products WHERE id = :id FOR UPDATE")
                .setParameter("id", product.getId())
                .getSingleResult();
        product.syncFromLockedRead(((Number) row[0]).intValue(), (BigDecimal) row[1]);
        return product;
    }

    private boolean allowNegativeStock(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::isAllowNegativeStock).orElse(false);
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
