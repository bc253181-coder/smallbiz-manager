package com.smallbiz.billing;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.billing.BillingDtos.CreatePaymentRequest;
import com.smallbiz.billing.BillingDtos.CurrentPlan;
import com.smallbiz.billing.BillingDtos.MethodView;
import com.smallbiz.billing.BillingDtos.PaymentOrderView;
import com.smallbiz.billing.BillingDtos.PlanLogView;
import com.smallbiz.billing.BillingDtos.PriceView;
import com.smallbiz.billing.BillingDtos.RedeemRequest;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

import jakarta.validation.Valid;

/** 商户端：套餐、激活码兑换、扫码付款。开通相关操作仅店主。 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final TenantRepository tenants;
    private final ProductRepository products;
    private final PlanPriceRepository prices;
    private final PaymentMethodRepository methods;
    private final PlanChangeLogRepository logs;
    private final ActivationService activation;
    private final PaymentService payments;
    private final PlanService planService;

    public BillingController(TenantRepository tenants, ProductRepository products, PlanPriceRepository prices,
            PaymentMethodRepository methods, PlanChangeLogRepository logs, ActivationService activation,
            PaymentService payments, PlanService planService) {
        this.planService = planService;
        this.tenants = tenants;
        this.products = products;
        this.prices = prices;
        this.methods = methods;
        this.logs = logs;
        this.activation = activation;
        this.payments = payments;
    }

    @GetMapping("/current")
    @Transactional(readOnly = true)
    public CurrentPlan current() {
        Long tenantId = CurrentUser.requireTenantId();
        Tenant t = tenants.findById(tenantId).orElseThrow();
        Instant now = Instant.now();
        Plan plan = t.effectivePlan(now);
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            features.put(f, PlanPolicy.allows(plan, f));
        }
        return new CurrentPlan(plan, plan == Plan.FREE ? null : t.getPlanExpiresAt(), t.isTrialUsed(),
                products.count(), PlanPolicy.maxProducts(plan), features, planService.upgradeBonusDays(t, Plan.PRO, now));
    }

    @GetMapping("/prices")
    public List<PriceView> prices(@RequestParam(defaultValue = "CNY") String currency) {
        CurrentUser.requireTenantId();
        return prices.findByCurrencyAndEnabledTrueOrderBySortOrderAsc(currency.toUpperCase()).stream()
                .map(PriceView::of).toList();
    }

    @GetMapping("/payment-methods")
    public List<MethodView> paymentMethods() {
        CurrentUser.requireTenantId();
        return methods.findByEnabledTrueOrderBySortOrderAsc().stream().map(MethodView::of).toList();
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<PlanLogView> history() {
        return logs.findTop50ByTenantIdOrderByIdDesc(CurrentUser.requireTenantId()).stream().map(PlanLogView::of).toList();
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasRole('OWNER')")
    public CurrentPlan redeem(@Valid @RequestBody RedeemRequest req) {
        activation.redeem(CurrentUser.requireTenantId(), CurrentUser.userId(), req.code());
        return current();
    }

    // ---------------- 扫码付款 ----------------

    @PostMapping("/payment-orders")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PaymentOrderView> createPayment(@Valid @RequestBody CreatePaymentRequest req) {
        PaymentOrder o = payments.create(CurrentUser.requireTenantId(), CurrentUser.userId(), req.priceId(),
                req.paymentMethodId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(o));
    }

    @GetMapping("/payment-orders")
    @PreAuthorize("hasRole('OWNER')")
    public List<PaymentOrderView> myPayments() {
        Map<Long, PaymentMethod> byId = methods.findAll().stream()
                .collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
        return payments.listMine(CurrentUser.requireTenantId()).stream()
                .map(o -> PaymentOrderView.of(o, byId.get(o.getPaymentMethodId()), null)).toList();
    }

    @GetMapping("/payment-orders/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public PaymentOrderView myPayment(@PathVariable Long id) {
        return view(payments.getMine(CurrentUser.requireTenantId(), id));
    }

    @PostMapping(path = "/payment-orders/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public PaymentOrderView submitProof(@PathVariable Long id, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String transactionRef, @RequestParam(required = false) String note) {
        if (transactionRef != null && transactionRef.length() > 100) {
            throw ApiException.badRequest("VALIDATION_FAILED", "交易单号过长");
        }
        return view(payments.submitProof(CurrentUser.requireTenantId(), id, file, transactionRef, note));
    }

    @PostMapping("/payment-orders/{id}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public PaymentOrderView cancelPayment(@PathVariable Long id) {
        return view(payments.cancelMine(CurrentUser.requireTenantId(), id));
    }

    @GetMapping("/payment-orders/{id}/proof-image")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> proofImage(@PathVariable Long id) {
        byte[] bytes = payments.proofImage(CurrentUser.requireTenantId(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(payments.proofContentType(id))).body(bytes);
    }

    private PaymentOrderView view(PaymentOrder o) {
        return PaymentOrderView.of(o, methods.findById(o.getPaymentMethodId()).orElse(null), null);
    }
}
