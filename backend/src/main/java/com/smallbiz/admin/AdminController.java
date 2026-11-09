package com.smallbiz.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.billing.ActivationCode;
import com.smallbiz.billing.ActivationCodeBatch;
import com.smallbiz.billing.ActivationCodeBatchRepository;
import com.smallbiz.billing.ActivationCodeRepository;
import com.smallbiz.billing.ActivationService;
import com.smallbiz.billing.BillingDtos.MethodView;
import com.smallbiz.billing.BillingDtos.PaymentOrderView;
import com.smallbiz.billing.BillingDtos.PriceView;
import com.smallbiz.billing.PaymentMethod;
import com.smallbiz.billing.PaymentMethodRepository;
import com.smallbiz.billing.PaymentOrder;
import com.smallbiz.billing.PaymentOrderRepository;
import com.smallbiz.billing.PaymentService;
import com.smallbiz.billing.PlanPrice;
import com.smallbiz.billing.PlanPriceRepository;
import com.smallbiz.billing.PlanService;
import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.Strings;
import com.smallbiz.security.CurrentUser;
import com.smallbiz.support.SupportFaq;
import com.smallbiz.support.SupportFaqRepository;
import com.smallbiz.support.UnmatchedQuery;
import com.smallbiz.support.UnmatchedQueryRepository;
import com.smallbiz.tenant.MemberRole;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantMember;
import com.smallbiz.tenant.TenantMemberRepository;
import com.smallbiz.tenant.TenantRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 平台后台（仅平台管理员）。只查看店铺的经营概况（名称、套餐、到期、店主联系方式），
 * 不进入店铺的业务数据。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {

    // ---------------- DTO ----------------

    public record Stats(long tenantCount, long paidTenantCount, long newTenantsThisMonth, long pendingPayments) {
    }

    public record TenantRow(Long id, String name, Plan plan, Plan rawPlan, Instant planExpiresAt, boolean trialUsed,
            String ownerEmail, String ownerName, Instant createdAt) {
    }

    public record SetPlanRequest(@NotNull(message = "请选择套餐") Plan plan, LocalDate expiresOn,
            @NotBlank(message = "请填写原因") @Size(max = 200) String note) {
    }

    public record BatchRequest(@NotBlank(message = "请填写批次名称") @Size(max = 100) String name,
            @NotNull Plan plan, @Min(1) @Max(3660) int durationDays, boolean trial,
            @Min(value = 1, message = "数量至少 1") @Max(value = 1000, message = "每批最多 1000 个") int quantity,
            LocalDate expiresOn) {
    }

    public record BatchRow(Long id, String name, Plan plan, int durationDays, boolean trial, int quantity, long redeemed,
            long voided, Instant expiresAt, Instant createdAt) {
    }

    public record CreatedBatch(BatchRow batch, List<String> codes) {
    }

    public record CodeRequest(@NotBlank(message = "请输入激活码") String code) {
    }

    public record RejectRequest(@NotBlank(message = "请填写拒绝原因") @Size(max = 200) String reason) {
    }

    public record PriceUpdate(@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            boolean enabled) {
    }

    public record MethodUpdate(@NotBlank @Size(max = 50) String displayName, @Size(max = 100) String payeeName,
            @Size(max = 500) String instructions, boolean enabled) {
    }

    public record FaqRequest(@NotBlank @Size(max = 30) String category, @NotBlank @Size(max = 200) String question,
            @NotBlank @Size(max = 500) String keywords, @NotBlank @Size(max = 4000) String answer,
            @Size(max = 30) String actionLabel, @Size(max = 100) String actionPath, boolean quickReply, int sortOrder,
            boolean enabled) {
    }

    public record FaqRow(Long id, String category, String question, String keywords, String answer, String actionLabel,
            String actionPath, boolean quickReply, int sortOrder, boolean enabled, int helpfulCount, int unhelpfulCount) {
        static FaqRow of(SupportFaq f) {
            return new FaqRow(f.getId(), f.getCategory(), f.getQuestion(), f.getKeywords(), f.getAnswer(),
                    f.getActionLabel(), f.getActionPath(), f.isQuickReply(), f.getSortOrder(), f.isEnabled(),
                    f.getHelpfulCount(), f.getUnhelpfulCount());
        }
    }

    public record UnmatchedRow(Long id, String queryText, int occurrences, Instant lastSeenAt) {
    }

    public record AmountView(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    // ---------------- 依赖 ----------------

    private final TenantRepository tenants;
    private final TenantMemberRepository members;
    private final UserRepository users;
    private final PlanService plans;
    private final ActivationService activation;
    private final ActivationCodeBatchRepository batches;
    private final ActivationCodeRepository codes;
    private final PaymentService payments;
    private final PaymentOrderRepository paymentOrders;
    private final PaymentMethodRepository methods;
    private final PlanPriceRepository prices;
    private final SupportFaqRepository faqs;
    private final UnmatchedQueryRepository unmatched;
    private final ImageStorage storage;
    private final com.smallbiz.billing.online.OnlinePaymentService online;

    public AdminController(TenantRepository tenants, TenantMemberRepository members, UserRepository users,
            PlanService plans, ActivationService activation, ActivationCodeBatchRepository batches,
            ActivationCodeRepository codes, PaymentService payments, PaymentOrderRepository paymentOrders,
            PaymentMethodRepository methods, PlanPriceRepository prices, SupportFaqRepository faqs,
            UnmatchedQueryRepository unmatched, ImageStorage storage,
            com.smallbiz.billing.online.OnlinePaymentService online) {
        this.online = online;
        this.tenants = tenants;
        this.members = members;
        this.users = users;
        this.plans = plans;
        this.activation = activation;
        this.batches = batches;
        this.codes = codes;
        this.payments = payments;
        this.paymentOrders = paymentOrders;
        this.methods = methods;
        this.prices = prices;
        this.faqs = faqs;
        this.unmatched = unmatched;
        this.storage = storage;
    }

    // ---------------- 概况与店铺 ----------------

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Tenant> all = tenants.findAll();
        long paid = all.stream().filter(t -> t.effectivePlan(now) != Plan.FREE).count();
        long fresh = all.stream().filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(monthStart)).count();
        return new Stats(all.size(), paid, fresh, paymentOrders.countByStatus(PaymentOrder.Status.PENDING_REVIEW));
    }

    @GetMapping("/tenants")
    @Transactional(readOnly = true)
    public List<TenantRow> tenants(@RequestParam(required = false) String q) {
        Instant now = Instant.now();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Tenant> all = tenants.findAll();
        Map<Long, TenantMember> owners = members.findAll().stream()
                .filter(m -> m.getRole() == MemberRole.OWNER && m.getStatus() == TenantMember.Status.ACTIVE)
                .collect(Collectors.toMap(TenantMember::getTenantId, Function.identity(), (a, b) -> a));
        Map<Long, User> userById = users.findAllById(owners.values().stream().map(TenantMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return all.stream()
                .map(t -> {
                    User owner = owners.containsKey(t.getId()) ? userById.get(owners.get(t.getId()).getUserId()) : null;
                    return new TenantRow(t.getId(), t.getName(), t.effectivePlan(now), t.getPlan(), t.getPlanExpiresAt(),
                            t.isTrialUsed(), owner == null ? null : owner.getEmail(),
                            owner == null ? null : owner.getDisplayName(), t.getCreatedAt());
                })
                .filter(r -> query.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(query)
                        || (r.ownerEmail() != null && r.ownerEmail().contains(query)))
                .sorted((x, y) -> Long.compare(y.id(), x.id()))
                .limit(500)
                .toList();
    }

    @PostMapping("/tenants/{id}/plan")
    @Transactional
    public TenantRow setPlan(@PathVariable Long id, @Valid @RequestBody SetPlanRequest req) {
        Instant expires = req.expiresOn() == null ? null : req.expiresOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        plans.setByAdmin(id, req.plan(), expires, CurrentUser.userId(), req.note().trim());
        return tenants(null).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    // ---------------- 激活码 ----------------

    @PostMapping("/code-batches")
    public ResponseEntity<CreatedBatch> createBatch(@Valid @RequestBody BatchRequest req) {
        Instant expires = req.expiresOn() == null ? null : req.expiresOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        ActivationService.CreatedBatch created = activation.createBatch(new ActivationService.BatchRequest(req.name(),
                req.plan(), req.durationDays(), req.trial(), req.quantity(), expires), CurrentUser.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedBatch(row(created.batch()), created.codes()));
    }

    @GetMapping("/code-batches")
    @Transactional(readOnly = true)
    public List<BatchRow> batches() {
        return batches.findAllByOrderByIdDesc().stream().map(this::row).toList();
    }

    @PostMapping("/codes/void")
    public Map<String, String> voidCode(@Valid @RequestBody CodeRequest req) {
        ActivationCode c = activation.voidCode(req.code());
        return Map.of("status", c.getStatus().name(), "hint", c.getCodeHint());
    }

    private BatchRow row(ActivationCodeBatch b) {
        return new BatchRow(b.getId(), b.getName(), b.getPlan(), b.getDurationDays(), b.isTrial(), b.getQuantity(),
                codes.countByBatchIdAndStatus(b.getId(), ActivationCode.Status.REDEEMED),
                codes.countByBatchIdAndStatus(b.getId(), ActivationCode.Status.VOIDED), b.getExpiresAt(), b.getCreatedAt());
    }

    // ---------------- 付款审核 ----------------

    @GetMapping("/payment-orders")
    @Transactional
    public List<PaymentOrderView> paymentOrders(@RequestParam(required = false) PaymentOrder.Status status,
            @RequestParam(defaultValue = "false") boolean anomaly) {
        List<PaymentOrder> list = anomaly ? payments.adminAnomalies() : payments.adminList(status);
        Map<Long, PaymentMethod> m = methods.findAll().stream().collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
        Map<Long, String> names = tenants.findAllById(list.stream().map(PaymentOrder::getTenantId).distinct().toList())
                .stream().collect(Collectors.toMap(Tenant::getId, Tenant::getName));
        return list.stream().map(o -> PaymentOrderView.of(o, m.get(o.getPaymentMethodId()), names.get(o.getTenantId())))
                .toList();
    }

    @GetMapping("/payment-orders/{id}/proof-image")
    public ResponseEntity<byte[]> proof(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(payments.proofContentType(id))).body(payments.proofImage(null, id));
    }

    @PostMapping("/payment-orders/{id}/approve")
    public PaymentOrderView approve(@PathVariable Long id) {
        return adminView(payments.approve(id, CurrentUser.userId()));
    }

    @PostMapping("/payment-orders/{id}/reject")
    public PaymentOrderView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return adminView(payments.reject(id, CurrentUser.userId(), req.reason()));
    }

    /** 异常已处理（例如已在支付渠道后台退款，或已手动调整套餐）。 */
    @PostMapping("/payment-orders/{id}/resolve-anomaly")
    public PaymentOrderView resolveAnomaly(@PathVariable Long id) {
        return adminView(payments.resolveAnomaly(id));
    }

    public record OnlineStatus(List<com.smallbiz.billing.online.OnlinePaymentService.Channel> channels, Instant lastReconcileAt, Integer lastChecked,
            Integer lastFixed, long openAnomalies) {
    }

    @GetMapping("/online-payments/status")
    public OnlineStatus onlineStatus() {
        var last = online.lastRun();
        List<com.smallbiz.billing.online.OnlinePaymentService.Channel> channels = java.util.stream.Stream.of("CNY", "MYR")
                .flatMap(c -> online.channels(c).stream()).distinct().toList();
        return new OnlineStatus(channels, last.map(r -> r.getCreatedAt()).orElse(null),
                last.map(r -> r.getChecked()).orElse(null), last.map(r -> r.getFixed()).orElse(null),
                paymentOrders.countByAnomalyIsNotNullAndAnomalyResolvedAtIsNull());
    }

    /** 立即对账一次（不用等 10 分钟）。 */
    @PostMapping("/online-payments/reconcile")
    public OnlineStatus reconcileNow() {
        online.reconcile();
        return onlineStatus();
    }

    private PaymentOrderView adminView(PaymentOrder o) {
        PaymentMethod m = o.getPaymentMethodId() == null ? null : methods.findById(o.getPaymentMethodId()).orElse(null);
        return PaymentOrderView.of(o, m, tenants.findById(o.getTenantId()).map(Tenant::getName).orElse(null));
    }

    // ---------------- 价格与收款方式 ----------------

    @GetMapping("/plan-prices")
    public List<PriceView> prices() {
        return prices.findAllByOrderByCurrencyAscSortOrderAsc().stream().map(PriceView::of).toList();
    }

    @PutMapping("/plan-prices/{id}")
    @Transactional
    public PriceView updatePrice(@PathVariable Long id, @Valid @RequestBody PriceUpdate req) {
        PlanPrice p = prices.findById(id).orElseThrow(() -> ApiException.notFound("PRICE_NOT_FOUND", "价格不存在"));
        p.setAmount(req.amount());
        p.setEnabled(req.enabled());
        return PriceView.of(p);
    }

    @GetMapping("/payment-methods")
    public List<MethodView> methods() {
        return methods.findAllByOrderBySortOrderAsc().stream().map(MethodView::of).toList();
    }

    @PutMapping("/payment-methods/{id}")
    @Transactional
    public MethodView updateMethod(@PathVariable Long id, @Valid @RequestBody MethodUpdate req) {
        PaymentMethod m = loadMethod(id);
        m.setDisplayName(req.displayName().trim());
        m.setPayeeName(Strings.blankToNull(req.payeeName()));
        m.setInstructions(Strings.blankToNull(req.instructions()));
        m.setEnabled(req.enabled());
        return MethodView.of(m);
    }

    @PostMapping(path = "/payment-methods/{id}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public MethodView uploadQr(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        PaymentMethod m = loadMethod(id);
        String old = m.getQrImagePath();
        m.setQrImagePath(storage.storePublic("platform", file));
        storage.delete(old);
        return MethodView.of(m);
    }

    private PaymentMethod loadMethod(Long id) {
        return methods.findById(id).orElseThrow(() -> ApiException.notFound("METHOD_NOT_FOUND", "收款方式不存在"));
    }

    // ---------------- 客服话术 ----------------

    @GetMapping("/support/faqs")
    public List<FaqRow> faqs() {
        return faqs.findAllByOrderBySortOrderAscIdAsc().stream().map(FaqRow::of).toList();
    }

    @PostMapping("/support/faqs")
    @Transactional
    public ResponseEntity<FaqRow> createFaq(@Valid @RequestBody FaqRequest req) {
        SupportFaq f = new SupportFaq();
        apply(f, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(FaqRow.of(faqs.save(f)));
    }

    @PutMapping("/support/faqs/{id}")
    @Transactional
    public FaqRow updateFaq(@PathVariable Long id, @Valid @RequestBody FaqRequest req) {
        SupportFaq f = faqs.findById(id).orElseThrow(() -> ApiException.notFound("FAQ_NOT_FOUND", "话术不存在"));
        apply(f, req);
        return FaqRow.of(f);
    }

    @DeleteMapping("/support/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqs.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/support/unmatched")
    public List<UnmatchedRow> unmatched() {
        return unmatched.findTop100ByResolvedFalseOrderByOccurrencesDescLastSeenAtDesc().stream()
                .map(u -> new UnmatchedRow(u.getId(), u.getQueryText(), u.getOccurrences(), u.getLastSeenAt())).toList();
    }

    @PostMapping("/support/unmatched/{id}/resolve")
    @Transactional
    public ResponseEntity<Void> resolve(@PathVariable Long id) {
        unmatched.findById(id).ifPresent(UnmatchedQuery::resolve);
        return ResponseEntity.noContent().build();
    }

    private static void apply(SupportFaq f, FaqRequest r) {
        String path = Strings.blankToNull(r.actionPath());
        if (path != null && !path.startsWith("/")) {
            throw ApiException.badRequest("INVALID_PATH", "跳转路径必须以 / 开头（站内页面）");
        }
        f.update(r.category().trim(), r.question().trim(), r.keywords().trim(), r.answer().trim(),
                Strings.blankToNull(r.actionLabel()), path, r.quickReply(), r.sortOrder(), r.enabled());
    }
}
