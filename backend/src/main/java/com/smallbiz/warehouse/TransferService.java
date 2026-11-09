package com.smallbiz.warehouse;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import com.smallbiz.tenant.SequenceService;
import com.smallbiz.warehouse.WarehouseDtos.TransferItemRequest;
import com.smallbiz.warehouse.WarehouseDtos.TransferItemView;
import com.smallbiz.warehouse.WarehouseDtos.TransferRequest;
import com.smallbiz.warehouse.WarehouseDtos.TransferView;

/**
 * 调拨：保存即生效。按商品 id 顺序对每个商品先调出、再调入（同一个商品行锁内完成），总库存不变；
 * 调出仓库不足时整单回滚。
 */
@Service
public class TransferService {

    public static final String REF_TYPE = "STOCK_TRANSFER";

    private final StockTransferRepository transfers;
    private final WarehouseService warehouses;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final SequenceService sequences;
    private final UserRepository users;

    public TransferService(StockTransferRepository transfers, WarehouseService warehouses, ProductRepository products,
            InventoryService inventory, SequenceService sequences, UserRepository users) {
        this.transfers = transfers;
        this.warehouses = warehouses;
        this.products = products;
        this.inventory = inventory;
        this.sequences = sequences;
        this.users = users;
    }

    @Transactional
    public TransferView create(Long tenantId, TransferRequest req, Long userId) {
        if (Objects.equals(req.fromWarehouseId(), req.toWarehouseId())) {
            throw ApiException.badRequest("SAME_WAREHOUSE", "调出和调入不能是同一个仓库");
        }
        Warehouse from = warehouses.resolve(tenantId, req.fromWarehouseId(), WarehouseService.Use.TRANSFER_OUT);
        Warehouse to = warehouses.resolve(tenantId, req.toWarehouseId(), WarehouseService.Use.OPERATE);
        Set<Long> seen = new HashSet<>();
        List<StockTransferItem> items = new java.util.ArrayList<>();
        for (TransferItemRequest r : req.items()) {
            if (!seen.add(r.productId())) {
                throw ApiException.badRequest("DUPLICATE_PRODUCT", "同一个商品不能重复添加，请合并数量");
            }
            Product p = products.findById(r.productId())
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "商品不存在或已删除"));
            items.add(new StockTransferItem(p.getId(), p.getName(), p.getSku(), p.getUnit(), r.quantity()));
        }
        StockTransfer t = new StockTransfer(sequences.documentNo(tenantId, "TF"), from.getId(), to.getId(),
                Strings.blankToNull(req.notes()), userId);
        t.setItems(items);
        t = transfers.saveAndFlush(t);
        String reason = "调拨 " + t.getTransferNo() + "：" + from.getName() + " → " + to.getName();
        for (StockTransferItem i : t.getItems().stream().sorted(Comparator.comparing(StockTransferItem::getProductId)).toList()) {
            inventory.apply(i.getProductId(), new InventoryService.Change(MovementType.TRANSFER_OUT, -i.getQuantity(),
                    null, REF_TYPE, t.getId(), reason, userId, from.getId()));
            inventory.apply(i.getProductId(), new InventoryService.Change(MovementType.TRANSFER_IN, i.getQuantity(),
                    null, REF_TYPE, t.getId(), reason, userId, to.getId()));
        }
        return toView(t, warehouses.names());
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferView> list(Long warehouseId, Pageable pageable) {
        Specification<StockTransfer> spec = (root, q, cb) -> cb.conjunction();
        if (warehouseId != null) {
            spec = spec.and((root, q, cb) -> cb.or(cb.equal(root.get("fromWarehouseId"), warehouseId),
                    cb.equal(root.get("toWarehouseId"), warehouseId)));
        }
        Map<Long, String> names = warehouses.names();
        return PageResponse.of(transfers.findAll(spec, pageable), t -> toView(t, names));
    }

    @Transactional(readOnly = true)
    public TransferView get(Long id) {
        StockTransfer t = transfers.findById(id)
                .orElseThrow(() -> ApiException.notFound("TRANSFER_NOT_FOUND", "调拨单不存在"));
        return toView(t, warehouses.names());
    }

    private TransferView toView(StockTransfer t, Map<Long, String> names) {
        String creator = t.getCreatedBy() == null ? null
                : users.findById(t.getCreatedBy()).map(User::getDisplayName).orElse(null);
        return new TransferView(t.getId(), t.getTransferNo(), t.getFromWarehouseId(), names.get(t.getFromWarehouseId()),
                t.getToWarehouseId(), names.get(t.getToWarehouseId()), t.getNotes(), t.getItemCount(),
                t.getTotalQuantity(), creator, t.getCreatedAt(),
                t.getItems().stream().map(i -> new TransferItemView(i.getProductId(), i.getProductName(), i.getSku(),
                        i.getUnit(), i.getQuantity())).collect(Collectors.toList()));
    }
}
