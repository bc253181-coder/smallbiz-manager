package com.smallbiz.excel;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.billing.Feature;
import com.smallbiz.billing.PlanGuard;
import com.smallbiz.common.ApiException;
import com.smallbiz.excel.ImportDtos.ImportResult;
import com.smallbiz.excel.ImportDtos.Mode;
import com.smallbiz.excel.ImportDtos.Type;
import com.smallbiz.security.CurrentUser;

/**
 * 导入：所有套餐都可以导入商品（免费版受商品数量上限约束）；客户、供应商需要对应的套餐功能。
 * 仅店主、管理员。
 */
@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ImportController {

    private final ImportService service;
    private final PlanGuard planGuard;

    public ImportController(ImportService service, PlanGuard planGuard) {
        this.service = service;
        this.planGuard = planGuard;
    }

    @GetMapping("/{type}/template")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        Type t = parse(type);
        requirePlan(t);
        return XlsxWriter.response(ImportService.label(t) + "导入模板.xlsx", service.template(t));
    }

    @PostMapping("/{type}")
    public ImportResult upload(@PathVariable String type, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "CREATE_ONLY") Mode mode, @RequestParam(defaultValue = "true") boolean dryRun) {
        Type t = parse(type);
        requirePlan(t);
        return service.run(t, file, mode, dryRun, CurrentUser.requireTenantId(), CurrentUser.userId());
    }

    private void requirePlan(Type t) {
        Long tenantId = CurrentUser.requireTenantId();
        if (t == Type.CUSTOMERS) {
            planGuard.require(tenantId, Feature.CUSTOMERS);
        } else if (t == Type.SUPPLIERS) {
            planGuard.require(tenantId, Feature.SUPPLIERS);
        } else if (t == Type.CUSTOMER_PRICES) {
            planGuard.require(tenantId, Feature.CUSTOMER_PRICES);
        }
    }

    static Type parse(String type) {
        return switch (type) {
            case "products" -> Type.PRODUCTS;
            case "customers" -> Type.CUSTOMERS;
            case "suppliers" -> Type.SUPPLIERS;
            case "customer-prices" -> Type.CUSTOMER_PRICES;
            default -> throw ApiException.notFound("IMPORT_TYPE_NOT_FOUND", "不支持导入这种数据");
        };
    }
}
