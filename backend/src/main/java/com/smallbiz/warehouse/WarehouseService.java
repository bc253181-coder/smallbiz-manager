package com.smallbiz.warehouse;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.Strings;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.TenantRepository;
import com.smallbiz.warehouse.WarehouseDtos.StockLine;
import com.smallbiz.warehouse.WarehouseDtos.WarehouseList;
import com.smallbiz.warehouse.WarehouseDtos.WarehouseRequest;
import com.smallbiz.warehouse.WarehouseDtos.WarehouseView;

/**
 * 仓库管理与"这个操作能不能用这个仓库"的检查。
 * 专业版最多 5 个仓库；到期后默认仓库照常使用，其他仓库只能调出（不能开单、入库、盘点、收货、调入）。
 */
@Service
public class WarehouseService {

    /** 操作类型：调出不受套餐限制（让到期的店能把货调回主仓库），其他操作非默认仓库需要专业版。 */
    public enum Use { OPERATE, TRANSFER_OUT }

    private final WarehouseRepository warehouses;
    private final ProductStockRepository stocks;
    private final DefaultWarehouse defaultWarehouse;
    private final PlanGuard planGuard;
    private final TenantRepository tenants;

    public WarehouseService(WarehouseRepository warehouses, ProductStockRepository stocks, DefaultWarehouse defaultWarehouse,
            PlanGuard planGuard, TenantRepository tenants) {
        this.warehouses = warehouses;
        this.stocks = stocks;
        this.defaultWarehouse = defaultWarehouse;
        this.planGuard = planGuard;
        this.tenants = tenants;
    }

    // ---------------- 查询 ----------------

    @Transactional
    public WarehouseList list(Long tenantId, boolean hideValue) {
        defaultWarehouse.id(tenantId); // 确保默认仓库存在
        Plan plan = planGuard.currentPlan(tenantId);
        Map<Long, Object[]> totals = new HashMap<>();
        stocks.warehouseTotals().forEach(r -> totals.put((Long) r[0], r));
        List<WarehouseView> views = warehouses.findAllOrdered().stream().map(w -> {
            Object[] t = totals.get(w.getId());
            return new WarehouseView(w.getId(), w.getName(), w.getAddress(), w.getNotes(), w.isDefault(), w.isActive(),
                    w.isActive() && (w.isDefault() || PlanPolicy.allows(plan, Feature.WAREHOUSES)),
                    t == null ? 0 : ((Number) t[1]).longValue(), t == null ? 0 : ((Number) t[2]).longValue(),
                    hideValue ? null : t == null ? BigDecimal.ZERO : ((BigDecimal) t[3]).setScale(2, java.math.RoundingMode.HALF_UP),
                    w.getVersion());
        }).toList();
        return new WarehouseList(views, PlanPolicy.maxWarehouses(plan), PlanPolicy.allows(plan, Feature.WAREHOUSES));
    }

    /** 某商品在各个（启用的或仍有库存的）仓库的库存。 */
    @Transactional(readOnly = true)
    public List<StockLine> productStocks(Long productId) {
        Map<Long, Integer> qty = stocks.findByProductId(productId).stream()
                .collect(Collectors.toMap(ProductStock::getWarehouseId, ProductStock::getQty));
        return warehouses.findAllOrdered().stream()
                .filter(w -> w.isActive() || qty.getOrDefault(w.getId(), 0) != 0)
                .map(w -> new StockLine(w.getId(), w.getName(), qty.getOrDefault(w.getId(), 0)))
                .toList();
    }

    /** 某仓库中这些商品的库存（开单、调拨时显示可用数量）。没有记录的为 0。 */
    @Transactional(readOnly = true)
    public Map<Long, Integer> quantities(Long warehouseId, Collection<Long> productIds) {
        load(warehouseId);
        Map<Long, Integer> result = new HashMap<>();
        productIds.forEach(id -> result.put(id, 0));
        stocks.findByWarehouseIdAndProductIdIn(warehouseId, productIds).forEach(s -> result.put(s.getProductId(), s.getQty()));
        return result;
    }

    // ---------------- 维护 ----------------

    @Transactional
    public Warehouse create(Long tenantId, WarehouseRequest req) {
        planGuard.require(tenantId, Feature.WAREHOUSES);
        defaultWarehouse.id(tenantId);
        // 锁店铺行：两个人同时新建时不会超过上限
        tenants.findByIdForUpdate(tenantId).orElseThrow();
        int max = PlanPolicy.maxWarehouses(planGuard.currentPlan(tenantId));
        if (warehouses.countByActiveTrue() >= max) {
            throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_LIMIT_REACHED", "最多 " + max + " 个仓库（停用的不计入）",
                    Map.of("max", max));
        }
        String name = req.name().trim();
        if (warehouses.existsByName(name)) {
            throw nameExists(name);
        }
        Warehouse w = new Warehouse(name, false, (int) warehouses.count());
        w.update(name, Strings.blankToNull(req.address()), Strings.blankToNull(req.notes()));
        try {
            return warehouses.saveAndFlush(w);
        } catch (DataIntegrityViolationException e) {
            throw nameExists(name);
        }
    }

    @Transactional
    public Warehouse update(Long id, WarehouseRequest req) {
        Warehouse w = load(id);
        String name = req.name().trim();
        if (!name.equals(w.getName()) && warehouses.existsByName(name)) {
            throw nameExists(name);
        }
        w.update(name, Strings.blankToNull(req.address()), Strings.blankToNull(req.notes()));
        try {
            return warehouses.saveAndFlush(w);
        } catch (DataIntegrityViolationException e) {
            throw nameExists(name);
        }
    }

    /** 停用：默认仓库不能停用；还有库存时先调走或盘点为 0。 */
    @Transactional
    public Warehouse deactivate(Long id) {
        Warehouse w = load(id);
        if (w.isDefault()) {
            throw ApiException.conflict("DEFAULT_WAREHOUSE", "默认仓库不能停用");
        }
        if (!w.isActive()) {
            return w;
        }
        if (stocks.hasStock(id)) {
            throw ApiException.conflict("WAREHOUSE_HAS_STOCK", "「" + w.getName() + "」还有库存，请先调拨到其他仓库或盘点为 0 再停用");
        }
        w.deactivate();
        return w;
    }

    // ---------------- 操作前检查 ----------------

    /**
     * 确定本次操作使用的仓库：为空时用默认仓库；必须属于本店、已启用；
     * 非默认仓库除调出外需要专业版。
     */
    @Transactional(readOnly = true)
    public Warehouse resolve(Long tenantId, Long warehouseId, Use use) {
        Warehouse w = warehouseId == null ? load(defaultWarehouse.id(tenantId)) : load(warehouseId);
        if (!w.isActive()) {
            throw ApiException.conflict("WAREHOUSE_INACTIVE", "仓库「" + w.getName() + "」已停用");
        }
        if (!w.isDefault() && use == Use.OPERATE
                && !PlanPolicy.allows(planGuard.currentPlan(tenantId), Feature.WAREHOUSES)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PLAN_FEATURE_REQUIRED",
                    "多仓库需要专业版。到期期间「" + w.getName() + "」只能把库存调拨出去，续费后恢复",
                    Map.of("feature", Feature.WAREHOUSES.name(), "requiredPlan", Plan.PRO.name()));
        }
        return w;
    }

    public Warehouse load(Long id) {
        return warehouses.findById(id).orElseThrow(() -> ApiException.notFound("WAREHOUSE_NOT_FOUND", "仓库不存在"));
    }

    public Map<Long, String> names() {
        return warehouses.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
    }

    public Map<Long, Warehouse> byId() {
        return warehouses.findAll().stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private static ApiException nameExists(String name) {
        return ApiException.conflict("WAREHOUSE_NAME_EXISTS", "已经有叫「" + name + "」的仓库了");
    }
}
