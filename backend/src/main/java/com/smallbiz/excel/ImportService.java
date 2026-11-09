package com.smallbiz.excel;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.billing.PlanGuard;
import com.smallbiz.billing.PlanPolicy;
import com.smallbiz.catalog.Category;
import com.smallbiz.catalog.CategoryDtos.CreateCategoryRequest;
import com.smallbiz.catalog.CategoryRepository;
import com.smallbiz.catalog.CategoryService;
import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductDtos.CreateProductRequest;
import com.smallbiz.catalog.ProductDtos.UpdateProductRequest;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.catalog.ProductService;
import com.smallbiz.catalog.ProductStatus;
import com.smallbiz.common.ApiException;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerDtos.CustomerRequest;
import com.smallbiz.customer.CustomerRepository;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.excel.ImportDtos.ImportResult;
import com.smallbiz.excel.ImportDtos.Mode;
import com.smallbiz.excel.ImportDtos.RowError;
import com.smallbiz.excel.ImportDtos.Type;
import com.smallbiz.excel.SheetReader.RowData;
import com.smallbiz.supplier.Supplier;
import com.smallbiz.supplier.SupplierDtos.SupplierRequest;
import com.smallbiz.supplier.SupplierRepository;
import com.smallbiz.supplier.SupplierService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * 导入商品、客户、供应商。
 * <ul>
 * <li>先逐行校验（预检与正式导入用同一套代码，正式导入前会再完整校验一遍）</li>
 * <li>只要有一行错误就整批不导入；正式导入在一个事务里完成，任何一行失败全部回滚</li>
 * <li>写入都通过现有业务方法（商品初始库存走库存流水，保证 stock_qty = 流水之和）</li>
 * </ul>
 */
@Service
public class ImportService {

    static final int MAX_UPLOADS_PER_MINUTE = 10;
    static final int MAX_ERRORS_SHOWN = 200;

    /** 模板列：header 为表头文字；property 为请求对象中的字段名（用于把校验错误对应到列）。 */
    record Col(String header, boolean required, String help, String example, String property) {
    }

    static final List<Col> PRODUCT_COLS = List.of(
            new Col("商品名称", true, "最多 200 字", "苹果数据线 1 米", "name"),
            new Col("单位", true, "如 个、条、箱", "条", "unit"),
            new Col("零售价", true, "数字，最多两位小数", "19.90", "retailPrice"),
            new Col("进货成本", true, "数字，最多两位小数", "8.50", "costPrice"),
            new Col("SKU", false, "商品编号，留空自动生成；只能包含字母、数字和 . _ - /；用来判断商品是否已存在", "CABLE-001", "sku"),
            new Col("条码", false, "没有 SKU 时按条码判断商品是否已存在", "6901234567890", "barcode"),
            new Col("分类", false, "不存在会自动新建；二级分类写成「数码 / 数据线」", "数码 / 数据线", "category"),
            new Col("批发价", false, "数字，最多两位小数", "15", "wholesalePrice"),
            new Col("库存预警值", false, "库存小于等于这个数时提醒补货，默认 0", "10", "lowStockThreshold"),
            new Col("初始库存", false, "只对新商品有效（已有商品的库存请用入库/盘点修改），默认 0", "100", "initialStock"),
            new Col("状态", false, "在售 或 停售，默认在售", "在售", "status"),
            new Col("描述", false, "最多 5000 字", "", "description"));

    static final List<Col> CUSTOMER_COLS = List.of(
            new Col("客户名称", true, "最多 100 字；没有电话时按名称判断客户是否已存在", "张三五金店", "name"),
            new Col("电话", false, "用来判断客户是否已存在", "13800138000", "phone"),
            new Col("邮箱", false, "", "", "email"),
            new Col("地址", false, "最多 300 字", "广州市天河区", "address"),
            new Col("类型", false, "个人 或 企业，默认个人", "企业", "customerType"),
            new Col("默认价格", false, "零售 或 批发（开单时自动带出的价格），默认零售", "批发", "priceTier"),
            new Col("备注", false, "最多 2000 字", "", "notes"));

    static final List<Col> SUPPLIER_COLS = List.of(
            new Col("供应商名称", true, "最多 100 字；按名称判断供应商是否已存在", "深圳华强配件厂", "name"),
            new Col("联系人", false, "", "李经理", "contactPerson"),
            new Col("电话", false, "", "0755-12345678", "phone"),
            new Col("邮箱", false, "", "", "email"),
            new Col("地址", false, "最多 300 字", "", "address"),
            new Col("供应商品", false, "最多 500 字", "数据线、充电头", "suppliedProductsNote"),
            new Col("备注", false, "最多 2000 字", "", "notes"));

    static List<Col> columns(Type type) {
        return switch (type) {
            case PRODUCTS -> PRODUCT_COLS;
            case CUSTOMERS -> CUSTOMER_COLS;
            case SUPPLIERS -> SUPPLIER_COLS;
        };
    }

    static String label(Type type) {
        return switch (type) {
            case PRODUCTS -> "商品";
            case CUSTOMERS -> "客户";
            case SUPPLIERS -> "供应商";
        };
    }

    /** 一行的处理计划：执行时调用 run。 */
    private record Action(int row, Runnable run) {
    }

    /** 预检的中间结果。 */
    private static final class Plan {
        final List<RowError> errors = new ArrayList<>();
        final List<Action> actions = new ArrayList<>();
        final Set<String> newCategories = new LinkedHashSet<>();
        final List<String> notes = new ArrayList<>();
        int created;
        int updated;
        int skipped;
    }

    private final ProductRepository products;
    private final ProductService productService;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final SupplierRepository suppliers;
    private final SupplierService supplierService;
    private final PlanGuard planGuard;
    private final Validator validator;
    private final Map<Long, Deque<Instant>> rate = new ConcurrentHashMap<>();

    public ImportService(ProductRepository products, ProductService productService, CategoryRepository categories,
            CategoryService categoryService, CustomerRepository customers, CustomerService customerService,
            SupplierRepository suppliers, SupplierService supplierService, PlanGuard planGuard, Validator validator) {
        this.products = products;
        this.productService = productService;
        this.categories = categories;
        this.categoryService = categoryService;
        this.customers = customers;
        this.customerService = customerService;
        this.suppliers = suppliers;
        this.supplierService = supplierService;
        this.planGuard = planGuard;
        this.validator = validator;
    }

    // ---------------- 模板 ----------------

    public byte[] template(Type type) {
        List<Col> cols = columns(type);
        List<List<Object>> help = cols.stream()
                .map(c -> List.<Object>of(c.header(), c.required() ? "必填" : "选填", c.help(), c.example()))
                .toList();
        return new XlsxWriter(ZoneId.of("UTC"))
                .textSheet(label(type), cols.stream().map(c -> c.required() ? c.header() + "*" : c.header()).toList())
                .sheet("填写说明", List.of(XlsxWriter.Column.text("列名"), XlsxWriter.Column.text("是否必填"),
                        XlsxWriter.Column.text("说明"), XlsxWriter.Column.text("示例")), help)
                .toBytes();
    }

    // ---------------- 预检 / 导入 ----------------

    @Transactional
    public ImportResult run(Type type, MultipartFile file, Mode mode, boolean dryRun, Long tenantId, Long userId) {
        checkRate(tenantId);
        SheetReader.Parsed parsed = SheetReader.read(file);
        List<String> missing = columns(type).stream().filter(Col::required).map(Col::header)
                .filter(h -> !parsed.headers().contains(h)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("IMPORT_MISSING_COLUMNS",
                    "缺少必填列：" + String.join("、", missing) + "。请使用导入模板，不要修改表头");
        }
        if (parsed.rows().isEmpty()) {
            throw ApiException.badRequest("IMPORT_FILE_EMPTY", "表格里没有数据，请从第 2 行开始填写");
        }

        Plan plan = switch (type) {
            case PRODUCTS -> planProducts(parsed.rows(), mode, tenantId, userId);
            case CUSTOMERS -> planCustomers(parsed.rows(), mode, userId);
            case SUPPLIERS -> planSuppliers(parsed.rows(), mode, userId);
        };

        boolean ok = plan.errors.isEmpty();
        if (ok && !dryRun) {
            for (Action a : plan.actions) {
                try {
                    a.run().run();
                } catch (ApiException e) {
                    // 整个事务回滚；把行号告诉用户
                    throw new ApiException(e.getStatus(), "IMPORT_ROW_FAILED", "第 " + a.row() + " 行：" + e.getMessage());
                }
            }
        }
        List<RowError> shown = plan.errors.size() > MAX_ERRORS_SHOWN ? plan.errors.subList(0, MAX_ERRORS_SHOWN) : plan.errors;
        if (plan.errors.size() > MAX_ERRORS_SHOWN) {
            plan.notes.add("共 " + plan.errors.size() + " 处错误，只显示前 " + MAX_ERRORS_SHOWN + " 处");
        }
        return new ImportResult(dryRun, ok && !dryRun, parsed.rows().size(), plan.created, plan.updated, plan.skipped,
                List.copyOf(shown), List.copyOf(plan.newCategories), List.copyOf(plan.notes));
    }

    // ---------------- 商品 ----------------

    private Plan planProducts(List<RowData> rows, Mode mode, Long tenantId, Long userId) {
        Plan plan = new Plan();
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Long> categoryCache = new HashMap<>();
        boolean stockIgnored = false;

        for (RowData r : rows) {
            List<RowError> errs = new ArrayList<>();
            String name = r.get("商品名称");
            String unit = r.get("单位");
            BigDecimal retail = money(r, "零售价", errs);
            BigDecimal cost = money(r, "进货成本", errs);
            BigDecimal wholesale = money(r, "批发价", errs);
            Integer threshold = integer(r, "库存预警值", errs);
            Integer initial = integer(r, "初始库存", errs);
            String sku = r.get("SKU");
            String barcode = r.get("条码");
            String description = r.get("描述");
            ProductStatus status = null;
            String s = r.get("状态");
            if (s != null) {
                status = switch (s) {
                    case "在售", "上架", "ACTIVE" -> ProductStatus.ACTIVE;
                    case "停售", "下架", "INACTIVE" -> ProductStatus.INACTIVE;
                    default -> {
                        errs.add(new RowError(r.rowNumber(), "状态", "只能填「在售」或「停售」"));
                        yield null;
                    }
                };
            }
            List<String> categoryPath = categoryPath(r, errs);

            String key = sku != null ? "SKU「" + sku + "」" : barcode != null ? "条码「" + barcode + "」" : null;
            if (key != null) {
                Integer first = seen.putIfAbsent(key, r.rowNumber());
                if (first != null) {
                    errs.add(new RowError(r.rowNumber(), sku != null ? "SKU" : "条码", key + "与第 " + first + " 行重复"));
                }
            }
            // 条码在店铺内唯一：有 SKU 的行也要检查条码是否与文件中其他行重复
            if (sku != null && barcode != null) {
                Integer first = seen.putIfAbsent("条码「" + barcode + "」", r.rowNumber());
                if (first != null) {
                    errs.add(new RowError(r.rowNumber(), "条码", "条码「" + barcode + "」与第 " + first + " 行重复"));
                }
            }
            if (!errs.isEmpty()) {
                plan.errors.addAll(errs);
                continue;
            }

            Optional<Product> existing = sku != null ? products.findBySku(sku)
                    : barcode != null ? products.findFirstByBarcodeOrderByIdAsc(barcode) : Optional.empty();
            if (barcode != null) {
                Optional<Product> owner = products.findFirstByBarcodeOrderByIdAsc(barcode);
                if (owner.isPresent() && (existing.isEmpty() || !owner.get().getId().equals(existing.get().getId()))) {
                    plan.errors.add(new RowError(r.rowNumber(), "条码",
                            "条码「" + barcode + "」已被商品「" + owner.get().getName() + "」使用"));
                    continue;
                }
            }
            if (existing.isPresent()) {
                if (mode == Mode.CREATE_ONLY) {
                    plan.skipped++;
                    continue;
                }
                Product p = existing.get();
                UpdateProductRequest req = new UpdateProductRequest(
                        name != null ? name : p.getName(), p.getSku(), p.getCategoryId(),
                        description != null ? description : p.getDescription(),
                        unit != null ? unit : p.getUnit(), barcode != null ? barcode : p.getBarcode(),
                        cost != null ? cost : p.getCostPrice(), retail != null ? retail : p.getRetailPrice(),
                        wholesale != null ? wholesale : p.getWholesalePrice(),
                        threshold != null ? threshold : p.getLowStockThreshold(),
                        status != null ? status : p.getStatus(), p.getVersion());
                if (validate(req, r.rowNumber(), PRODUCT_COLS, plan)) {
                    noteNewCategory(categoryPath, plan);
                    if (initial != null && initial > 0) {
                        stockIgnored = true;
                    }
                    plan.updated++;
                    Long id = p.getId();
                    plan.actions.add(new Action(r.rowNumber(), () -> {
                        Long categoryId = categoryPath == null ? req.categoryId() : resolveCategory(categoryPath, categoryCache);
                        productService.update(id, new UpdateProductRequest(req.name(), req.sku(), categoryId,
                                req.description(), req.unit(), req.barcode(), req.costPrice(), req.retailPrice(),
                                req.wholesalePrice(), req.lowStockThreshold(), req.status(), req.version()));
                    }));
                }
            } else {
                CreateProductRequest req = new CreateProductRequest(name, sku, null, description, unit, barcode, cost,
                        retail, wholesale, threshold == null ? 0 : threshold, status, initial == null ? 0 : initial);
                if (validate(req, r.rowNumber(), PRODUCT_COLS, plan)) {
                    noteNewCategory(categoryPath, plan);
                    plan.created++;
                    plan.actions.add(new Action(r.rowNumber(), () -> {
                        Long categoryId = categoryPath == null ? null : resolveCategory(categoryPath, categoryCache);
                        productService.create(new CreateProductRequest(req.name(), req.sku(), categoryId,
                                req.description(), req.unit(), req.barcode(), req.costPrice(), req.retailPrice(),
                                req.wholesalePrice(), req.lowStockThreshold(), req.status(), req.initialStock()),
                                tenantId, userId);
                    }));
                }
            }
        }

        Integer max = PlanPolicy.maxProducts(planGuard.currentPlan(tenantId));
        if (max != null && products.count() + plan.created > max) {
            plan.errors.add(0, new RowError(0, null, "免费版最多 " + max + " 个商品，现有 " + products.count()
                    + " 个，本次要新增 " + plan.created + " 个。升级标准版可不限数量"));
        }
        if (stockIgnored) {
            plan.notes.add("已有商品的「初始库存」不会导入：库存请通过入库、出库或盘点修改，以便追溯");
        }
        return plan;
    }

    /** 「数码 / 数据线」→ [数码, 数据线]；最多两级。 */
    private static List<String> categoryPath(RowData r, List<RowError> errs) {
        String raw = r.get("分类");
        if (raw == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String p : raw.split("[/／>]")) {
            if (!p.isBlank()) {
                parts.add(p.trim());
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() > 2) {
            errs.add(new RowError(r.rowNumber(), "分类", "最多两级分类，例如「数码 / 数据线」"));
        } else if (parts.stream().anyMatch(p -> p.length() > 50)) {
            errs.add(new RowError(r.rowNumber(), "分类", "分类名称最多 50 字"));
        }
        return parts;
    }

    private void noteNewCategory(List<String> path, Plan plan) {
        if (path == null) {
            return;
        }
        Optional<Category> parent = categories.findFirstByParentIdIsNullAndName(path.get(0));
        if (parent.isEmpty()) {
            plan.newCategories.add(String.join(" / ", path));
        } else if (path.size() == 2 && categories.findFirstByParentIdAndName(parent.get().getId(), path.get(1)).isEmpty()) {
            plan.newCategories.add(String.join(" / ", path));
        }
    }

    private Long resolveCategory(List<String> path, Map<String, Long> cache) {
        return cache.computeIfAbsent(String.join("/", path), k -> {
            Long parentId = categories.findFirstByParentIdIsNullAndName(path.get(0)).map(Category::getId)
                    .orElseGet(() -> categoryService.create(new CreateCategoryRequest(path.get(0), null, null)).id());
            if (path.size() == 1) {
                return parentId;
            }
            return categories.findFirstByParentIdAndName(parentId, path.get(1)).map(Category::getId)
                    .orElseGet(() -> categoryService.create(new CreateCategoryRequest(path.get(1), parentId, null)).id());
        });
    }

    // ---------------- 客户 ----------------

    private Plan planCustomers(List<RowData> rows, Mode mode, Long userId) {
        Plan plan = new Plan();
        Map<String, Integer> seen = new HashMap<>();
        for (RowData r : rows) {
            List<RowError> errs = new ArrayList<>();
            String name = r.get("客户名称");
            String phone = r.get("电话");
            Customer.Type type = null;
            String t = r.get("类型");
            if (t != null) {
                type = switch (t) {
                    case "个人", "INDIVIDUAL" -> Customer.Type.INDIVIDUAL;
                    case "企业", "公司", "BUSINESS" -> Customer.Type.BUSINESS;
                    default -> {
                        errs.add(new RowError(r.rowNumber(), "类型", "只能填「个人」或「企业」"));
                        yield null;
                    }
                };
            }
            Customer.PriceTier tier = null;
            String p = r.get("默认价格");
            if (p != null) {
                tier = switch (p) {
                    case "零售", "零售价", "RETAIL" -> Customer.PriceTier.RETAIL;
                    case "批发", "批发价", "WHOLESALE" -> Customer.PriceTier.WHOLESALE;
                    default -> {
                        errs.add(new RowError(r.rowNumber(), "默认价格", "只能填「零售」或「批发」"));
                        yield null;
                    }
                };
            }
            String key = phone != null ? "电话「" + phone + "」" : name != null ? "客户「" + name + "」" : null;
            if (key != null) {
                Integer first = seen.putIfAbsent(key, r.rowNumber());
                if (first != null) {
                    errs.add(new RowError(r.rowNumber(), phone != null ? "电话" : "客户名称", key + "与第 " + first + " 行重复"));
                }
            }
            if (!errs.isEmpty()) {
                plan.errors.addAll(errs);
                continue;
            }
            Optional<Customer> existing = phone != null ? customers.findFirstByPhoneOrderByIdAsc(phone)
                    : name != null ? customers.findFirstByNameOrderByIdAsc(name) : Optional.empty();
            if (existing.isPresent()) {
                if (mode == Mode.CREATE_ONLY) {
                    plan.skipped++;
                    continue;
                }
                Customer c = existing.get();
                CustomerRequest req = new CustomerRequest(name != null ? name : c.getName(),
                        phone != null ? phone : c.getPhone(), or(r.get("邮箱"), c.getEmail()),
                        or(r.get("地址"), c.getAddress()), type != null ? type : c.getCustomerType(),
                        tier != null ? tier : c.getPriceTier(), or(r.get("备注"), c.getNotes()), c.getVersion());
                if (validate(req, r.rowNumber(), CUSTOMER_COLS, plan)) {
                    plan.updated++;
                    Long id = c.getId();
                    plan.actions.add(new Action(r.rowNumber(), () -> customerService.update(id, req)));
                }
            } else {
                CustomerRequest req = new CustomerRequest(name, phone, r.get("邮箱"), r.get("地址"),
                        type != null ? type : Customer.Type.INDIVIDUAL, tier != null ? tier : Customer.PriceTier.RETAIL,
                        r.get("备注"), null);
                if (validate(req, r.rowNumber(), CUSTOMER_COLS, plan)) {
                    plan.created++;
                    plan.actions.add(new Action(r.rowNumber(), () -> customerService.create(req, userId)));
                }
            }
        }
        return plan;
    }

    // ---------------- 供应商 ----------------

    private Plan planSuppliers(List<RowData> rows, Mode mode, Long userId) {
        Plan plan = new Plan();
        Map<String, Integer> seen = new HashMap<>();
        for (RowData r : rows) {
            String name = r.get("供应商名称");
            if (name != null) {
                Integer first = seen.putIfAbsent(name, r.rowNumber());
                if (first != null) {
                    plan.errors.add(new RowError(r.rowNumber(), "供应商名称", "供应商「" + name + "」与第 " + first + " 行重复"));
                    continue;
                }
            }
            Optional<Supplier> existing = name == null ? Optional.empty() : suppliers.findFirstByNameOrderByIdAsc(name);
            if (existing.isPresent()) {
                if (mode == Mode.CREATE_ONLY) {
                    plan.skipped++;
                    continue;
                }
                Supplier s = existing.get();
                SupplierRequest req = new SupplierRequest(name, or(r.get("联系人"), s.getContactPerson()),
                        or(r.get("电话"), s.getPhone()), or(r.get("邮箱"), s.getEmail()), or(r.get("地址"), s.getAddress()),
                        or(r.get("供应商品"), s.getSuppliedProductsNote()), or(r.get("备注"), s.getNotes()), s.getVersion());
                if (validate(req, r.rowNumber(), SUPPLIER_COLS, plan)) {
                    plan.updated++;
                    Long id = s.getId();
                    plan.actions.add(new Action(r.rowNumber(), () -> supplierService.update(id, req)));
                }
            } else {
                SupplierRequest req = new SupplierRequest(name, r.get("联系人"), r.get("电话"), r.get("邮箱"),
                        r.get("地址"), r.get("供应商品"), r.get("备注"), null);
                if (validate(req, r.rowNumber(), SUPPLIER_COLS, plan)) {
                    plan.created++;
                    plan.actions.add(new Action(r.rowNumber(), () -> supplierService.create(req, userId)));
                }
            }
        }
        return plan;
    }

    // ---------------- 校验与解析 ----------------

    /** 用接口同一套校验规则（@NotBlank、@Size…）检查，把错误对应到表格的列。 */
    private <T> boolean validate(T req, int row, List<Col> cols, Plan plan) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        for (ConstraintViolation<T> v : violations) {
            String prop = v.getPropertyPath().toString();
            String header = cols.stream().filter(c -> c.property().equals(prop)).map(Col::header).findFirst().orElse(null);
            plan.errors.add(new RowError(row, header, v.getMessage()));
        }
        return violations.isEmpty();
    }

    /** 金额：允许 ¥、RM、千分位逗号；空白返回 null。 */
    static BigDecimal money(RowData r, String col, List<RowError> errs) {
        String v = r.get(col);
        if (v == null) {
            return null;
        }
        String s = v.replace("¥", "").replace("￥", "").replace("RM", "").replace(",", "").replace("，", "").trim();
        try {
            BigDecimal d = new BigDecimal(s);
            if (d.scale() > 2) {
                d = d.stripTrailingZeros();
            }
            return d.scale() < 0 ? d.setScale(0) : d;
        } catch (NumberFormatException e) {
            errs.add(new RowError(r.rowNumber(), col, "必须是数字"));
            return null;
        }
    }

    static Integer integer(RowData r, String col, List<RowError> errs) {
        String v = r.get(col);
        if (v == null) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v.replace(",", "").trim());
            if (d.stripTrailingZeros().scale() > 0) {
                errs.add(new RowError(r.rowNumber(), col, "必须是整数"));
                return null;
            }
            return d.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            errs.add(new RowError(r.rowNumber(), col, "必须是整数"));
            return null;
        }
    }

    private static String or(String v, String fallback) {
        return v != null ? v : fallback;
    }

    private void checkRate(Long tenantId) {
        Instant now = Instant.now();
        Deque<Instant> q = rate.computeIfAbsent(tenantId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_UPLOADS_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "上传太频繁了，请稍后再试");
            }
            q.addLast(now);
        }
    }
}
