package com.smallbiz.inventory;

import java.math.BigDecimal;
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
        product.applyStockChange((int) after);
        return movements.save(new StockMovement(product.getId(), change.type(), change.qtyChange(), before,
                change.unitCost(), change.refType(), change.refId(), trimToNull(change.reason()),
                change.operatorId()));
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
