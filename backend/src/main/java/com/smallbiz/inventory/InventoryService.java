package com.smallbiz.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/**
 * 库存的唯一修改入口。
 *
 * <p>每次调用在调用方的事务中：
 * <ol>
 *   <li>对商品行加写锁（SELECT … FOR UPDATE），并发扣减时排队执行，不会超卖；</li>
 *   <li>校验库存是否足够（除非店铺允许负库存）；</li>
 *   <li>更新商品当前库存，并写入一条流水。</li>
 * </ol>
 * 不变量：商品当前库存 == 该商品所有流水 qty_change 之和。
 *
 * <p>多个商品一起变动时（例如一张订单），调用方应按商品 id 升序调用，避免死锁。
 */
@Service
public class InventoryService {

    public record Change(MovementType type, int qtyChange, BigDecimal unitCost, String refType, Long refId,
            String reason, Long operatorId) {
    }

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final TenantRepository tenants;

    public InventoryService(ProductRepository products, StockMovementRepository movements, TenantRepository tenants) {
        this.products = products;
        this.movements = movements;
        this.tenants = tenants;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement apply(Long productId, Change change) {
        if (change.qtyChange() == 0) {
            throw ApiException.badRequest("ZERO_QUANTITY", "变动数量不能为 0");
        }
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        int before = product.getStockQty();
        long after = (long) before + change.qtyChange();
        if (after > Integer.MAX_VALUE || after < Integer.MIN_VALUE) {
            throw ApiException.badRequest("QUANTITY_OUT_OF_RANGE", "库存数量超出范围");
        }
        if (after < 0 && change.qtyChange() < 0 && !allowNegativeStock(product.getTenantId())) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "「" + product.getName() + "」库存不足",
                    Map.of("productId", product.getId(), "available", before, "requested", -change.qtyChange()));
        }
        if (change.qtyChange() > 0 && change.unitCost() != null
                && (change.type() == MovementType.STOCK_IN || change.type() == MovementType.PURCHASE_RECEIVE)) {
            product.setCostPrice(weightedAverageCost(before, product.getCostPrice(), change.qtyChange(),
                    change.unitCost()));
        }
        product.applyStockChange((int) after);
        return movements.save(new StockMovement(product.getId(), change.type(), change.qtyChange(), before,
                change.unitCost(), change.refType(), change.refId(), trimToNull(change.reason()),
                change.operatorId()));
    }

    /**
     * 盘点：把库存调整为实际清点的数量，差额记为一条 ADJUSTMENT 流水。
     * 先加锁再计算差额，避免并发时按过期的库存计算。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement adjustTo(Long productId, int actualQty, String reason, Long operatorId) {
        if (actualQty < 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "实际数量不能为负数");
        }
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在"));
        int delta = actualQty - product.getStockQty();
        if (delta == 0) {
            throw ApiException.badRequest("NO_CHANGE", "实际数量与当前库存相同，无需调整");
        }
        return apply(productId, new Change(MovementType.ADJUSTMENT, delta, null, null, null, reason, operatorId));
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
