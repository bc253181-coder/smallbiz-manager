package com.smallbiz.excel;

import static com.smallbiz.excel.XlsxWriter.Column.dateTime;
import static com.smallbiz.excel.XlsxWriter.Column.integer;
import static com.smallbiz.excel.XlsxWriter.Column.money;
import static com.smallbiz.excel.XlsxWriter.Column.text;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.catalog.ProductDtos.ProductView;
import com.smallbiz.catalog.ProductService;
import com.smallbiz.catalog.ProductStatus;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerDtos.CustomerView;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.inventory.InventoryDtos.MovementRow;
import com.smallbiz.inventory.InventoryQueryService;
import com.smallbiz.inventory.MovementType;
import com.smallbiz.purchasing.PurchaseDtos.PurchaseSummary;
import com.smallbiz.purchasing.PurchaseOrder;
import com.smallbiz.purchasing.PurchaseOrderItem;
import com.smallbiz.purchasing.PurchaseOrderRepository;
import com.smallbiz.purchasing.PurchaseService;
import com.smallbiz.purchasing.PurchaseStatus;
import com.smallbiz.sales.SalesDtos.SaleSummary;
import com.smallbiz.sales.SalesOrder;
import com.smallbiz.sales.SalesOrderItem;
import com.smallbiz.sales.SalesOrderRepository;
import com.smallbiz.sales.SalesService;
import com.smallbiz.supplier.SupplierDtos.SupplierView;
import com.smallbiz.supplier.SupplierService;
import com.smallbiz.tenant.TenantRepository;

/**
 * Excel 导出（专业版）。筛选条件与各列表页一致（复用列表查询），超过上限时提示缩小范围。
 * 时间按店铺时区写入。
 */
@Service
public class ExportService {

    static final int MAX_ROWS = 50_000;

    private final ProductService productService;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final SalesService salesService;
    private final SalesOrderRepository salesOrders;
    private final PurchaseService purchaseService;
    private final PurchaseOrderRepository purchaseOrders;
    private final InventoryQueryService inventoryQuery;
    private final TenantRepository tenants;

    public ExportService(ProductService productService, CustomerService customerService, SupplierService supplierService,
            SalesService salesService, SalesOrderRepository salesOrders, PurchaseService purchaseService,
            PurchaseOrderRepository purchaseOrders, InventoryQueryService inventoryQuery, TenantRepository tenants) {
        this.productService = productService;
        this.customerService = customerService;
        this.supplierService = supplierService;
        this.salesService = salesService;
        this.salesOrders = salesOrders;
        this.purchaseService = purchaseService;
        this.purchaseOrders = purchaseOrders;
        this.inventoryQuery = inventoryQuery;
        this.tenants = tenants;
    }

    public ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(t -> ZoneId.of(t.getTimezone())).orElse(ZoneId.of("Asia/Shanghai"));
    }

    private static PageRequest all(Sort sort) {
        return PageRequest.of(0, MAX_ROWS + 1, sort);
    }

    private static <T> List<T> items(PageResponse<T> page) {
        if (page.totalItems() > MAX_ROWS) {
            throw ApiException.badRequest("EXPORT_TOO_LARGE",
                    "一次最多导出 " + MAX_ROWS + " 行（当前 " + page.totalItems() + " 行），请缩小日期范围或筛选条件");
        }
        return page.items();
    }

    // ---------------- 商品 ----------------

    @Transactional(readOnly = true)
    public byte[] products(Long tenantId, ProductService.Filter f) {
        List<ProductView> list = items(productService.list(f, all(Sort.by("name", "id")), false));
        List<List<Object>> rows = list.stream().map(p -> Arrays.<Object>asList(p.name(), p.sku(), p.barcode(),
                p.categoryName(), p.unit(), p.costPrice(), p.retailPrice(), p.wholesalePrice(), p.stockQty(),
                p.costPrice() == null ? null : p.costPrice().multiply(BigDecimal.valueOf(p.stockQty())),
                p.lowStockThreshold(), p.status() == ProductStatus.ACTIVE ? "在售" : "停售")).toList();
        return new XlsxWriter(zone(tenantId)).sheet("商品", List.of(text("商品名称"), text("SKU"), text("条码"),
                text("分类"), text("单位"), money("进货成本"), money("零售价"), money("批发价"), integer("库存"),
                money("库存价值"), integer("库存预警值"), text("状态")), rows).toBytes();
    }

    // ---------------- 客户 / 供应商 ----------------

    @Transactional(readOnly = true)
    public byte[] customers(Long tenantId, CustomerService.Filter f) {
        List<CustomerView> list = items(customerService.list(f, all(Sort.by("name", "id"))));
        Map<Long, Object[]> totals = new HashMap<>();
        salesOrders.customerTotals().forEach(r -> totals.put((Long) r[0], r));
        List<List<Object>> rows = list.stream().map(c -> {
            Object[] t = totals.get(c.id());
            return Arrays.<Object>asList(c.name(), c.phone(), c.email(), c.address(),
                    c.customerType() == Customer.Type.BUSINESS ? "企业" : "个人",
                    c.priceTier() == Customer.PriceTier.WHOLESALE ? "批发" : "零售",
                    t == null ? 0 : ((Number) t[1]).intValue(), t == null ? BigDecimal.ZERO : t[2],
                    t == null ? BigDecimal.ZERO : t[3], c.notes(), c.createdAt());
        }).toList();
        return new XlsxWriter(zone(tenantId)).sheet("客户", List.of(text("客户名称"), text("电话"), text("邮箱"),
                text("地址"), text("类型"), text("默认价格"), integer("订单数"), money("累计消费"), money("当前欠款"),
                text("备注"), dateTime("创建时间")), rows).toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] suppliers(Long tenantId, String q) {
        List<SupplierView> list = items(supplierService.list(q, all(Sort.by("name", "id"))));
        List<List<Object>> rows = list.stream().map(s -> Arrays.<Object>asList(s.name(), s.contactPerson(), s.phone(),
                s.email(), s.address(), s.suppliedProductsNote(), s.notes(), s.createdAt())).toList();
        return new XlsxWriter(zone(tenantId)).sheet("供应商", List.of(text("供应商名称"), text("联系人"), text("电话"),
                text("邮箱"), text("地址"), text("供应商品"), text("备注"), dateTime("创建时间")), rows).toBytes();
    }

    // ---------------- 销售 ----------------

    @Transactional(readOnly = true)
    public byte[] sales(Long tenantId, SalesService.Filter f) {
        List<SaleSummary> list = items(salesService.list(tenantId, f, all(Sort.by("orderDate", "id"))));
        Map<Long, SalesOrder> orders = new HashMap<>();
        salesOrders.findAllById(list.stream().map(SaleSummary::id).toList()).forEach(o -> orders.put(o.getId(), o));

        List<List<Object>> head = new ArrayList<>();
        List<List<Object>> lines = new ArrayList<>();
        for (SaleSummary s : list) {
            SalesOrder o = orders.get(s.id());
            boolean cancelled = o.getStatus() == SalesOrder.Status.CANCELLED;
            head.add(Arrays.asList(o.getOrderNo(), o.getOrderDate(), o.getCustomerName() == null ? "散客" : o.getCustomerName(),
                    cancelled ? "已取消" : "已完成", o.getTotalQuantity(), o.getSubtotal(), o.getDiscountAmount(),
                    o.getTotalAmount(), o.getTotalCost(), o.getTotalAmount().subtract(o.getTotalCost()), o.getPaidAmount(),
                    o.getTotalAmount().subtract(o.getPaidAmount()), s.createdByName(), o.getNotes()));
            for (SalesOrderItem i : o.getItems()) {
                BigDecimal cost = i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity()));
                lines.add(Arrays.asList(o.getOrderNo(), o.getOrderDate(), cancelled ? "已取消" : "已完成",
                        i.getProductName(), i.getSku(), i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal(),
                        i.getUnitCost(), cost, i.getLineTotal().subtract(cost)));
            }
        }
        return new XlsxWriter(zone(tenantId))
                .sheet("销售单", List.of(text("单号"), dateTime("开单时间"), text("客户"), text("状态"), integer("件数"),
                        money("商品金额"), money("整单优惠"), money("应收"), money("成本"), money("毛利"), money("已收"),
                        money("未收"), text("开单人"), text("备注")), head)
                .sheet("销售明细", List.of(text("单号"), dateTime("开单时间"), text("状态"), text("商品"), text("SKU"),
                        text("单位"), integer("数量"), money("单价"), money("小计"), money("单位成本"), money("成本"),
                        money("毛利")), lines)
                .toBytes();
    }

    // ---------------- 采购 ----------------

    @Transactional(readOnly = true)
    public byte[] purchases(Long tenantId, PurchaseService.Filter f) {
        List<PurchaseSummary> list = items(purchaseService.list(tenantId, f, all(Sort.by("orderDate", "id"))));
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        purchaseOrders.findAllById(list.stream().map(PurchaseSummary::id).toList()).forEach(o -> orders.put(o.getId(), o));

        List<List<Object>> head = new ArrayList<>();
        List<List<Object>> lines = new ArrayList<>();
        for (PurchaseSummary s : list) {
            PurchaseOrder o = orders.get(s.id());
            String status = purchaseStatus(o.getStatus());
            head.add(Arrays.asList(o.getOrderNo(), o.getOrderDate(), o.getSupplierName(), status, o.getTotalQuantity(),
                    o.getTotalAmount(), o.getReceivedAt(), o.getNotes()));
            for (PurchaseOrderItem i : o.getItems()) {
                lines.add(Arrays.asList(o.getOrderNo(), o.getOrderDate(), o.getSupplierName(), status, i.getProductName(),
                        i.getSku(), i.getUnit(), i.getQuantity(), i.getUnitCost(), i.getLineTotal()));
            }
        }
        return new XlsxWriter(zone(tenantId))
                .sheet("采购单", List.of(text("单号"), dateTime("下单时间"), text("供应商"), text("状态"), integer("件数"),
                        money("金额"), dateTime("收货时间"), text("备注")), head)
                .sheet("采购明细", List.of(text("单号"), dateTime("下单时间"), text("供应商"), text("状态"), text("商品"),
                        text("SKU"), text("单位"), integer("数量"), money("进货单价"), money("小计")), lines)
                .toBytes();
    }

    private static String purchaseStatus(PurchaseStatus s) {
        return switch (s) {
            case DRAFT -> "草稿";
            case ORDERED -> "已下单";
            case RECEIVED -> "已收货";
            case CANCELLED -> "已取消";
        };
    }

    // ---------------- 库存流水 ----------------

    @Transactional(readOnly = true)
    public byte[] movements(Long tenantId, InventoryQueryService.MovementFilter f) {
        List<MovementRow> list = items(inventoryQuery.movements(tenantId, f, all(Sort.by("createdAt", "id")), false));
        List<List<Object>> rows = list.stream().map(m -> Arrays.<Object>asList(m.createdAt(), m.warehouseName(),
                m.productName(), m.productSku(), movementType(m.type()), m.qtyChange(), m.qtyBefore(), m.qtyAfter(), m.productUnit(), m.unitCost(), m.reason(),
                m.operatorName())).toList();
        return new XlsxWriter(zone(tenantId)).sheet("库存流水", List.of(dateTime("时间"), text("仓库"), text("商品"), text("SKU"),
                text("类型"), integer("变动数量"), integer("变动前"), integer("变动后"), text("单位"), money("单位成本"),
                text("原因"), text("操作人")), rows).toBytes();
    }

    static String movementType(MovementType t) {
        return switch (t) {
            case INITIAL -> "初始库存";
            case STOCK_IN -> "入库";
            case STOCK_OUT -> "出库";
            case ADJUSTMENT -> "盘点";
            case SALE -> "销售";
            case SALE_CANCEL -> "销售取消";
            case PURCHASE_RECEIVE -> "采购收货";
            case TRANSFER_OUT -> "调拨调出";
            case TRANSFER_IN -> "调拨调入";
        };
    }
}
