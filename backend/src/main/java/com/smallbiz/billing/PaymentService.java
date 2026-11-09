package com.smallbiz.billing;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.Strings;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

/**
 * 站内扫码付款 + 人工审核。
 *
 * <p>付款单不使用自动店铺隔离（平台管理员要跨店铺审核），因此商户端方法一律通过
 * {@link #loadMine(Long, Long)} 显式校验 tenantId；其他店铺的付款单一律 404。
 */
@Service
public class PaymentService {

    static final Duration PAY_WINDOW = Duration.ofHours(24);
    static final Duration ONLINE_PAY_WINDOW = Duration.ofMinutes(30);
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PaymentOrderRepository orders;
    private final PlanPriceRepository prices;
    private final PaymentMethodRepository methods;
    private final TenantRepository tenants;
    private final PlanService plans;
    private final com.smallbiz.billing.membership.MembershipService membership;
    private final ImageStorage storage;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(PaymentOrderRepository orders, PlanPriceRepository prices, PaymentMethodRepository methods,
            TenantRepository tenants, PlanService plans, ImageStorage storage,
            com.smallbiz.billing.membership.MembershipService membership) {
        this.membership = membership;
        this.orders = orders;
        this.prices = prices;
        this.methods = methods;
        this.tenants = tenants;
        this.plans = plans;
        this.storage = storage;
    }

    // ---------------- 商户端 ----------------

    @Transactional
    public PaymentOrder create(Long tenantId, Long userId, Long priceId, Long methodId) {
        PlanPrice price = prices.findById(priceId).filter(PlanPrice::isEnabled)
                .orElseThrow(() -> ApiException.notFound("PRICE_NOT_FOUND", "该套餐暂不可购买"));
        PaymentMethod method = methods.findById(methodId).filter(PaymentMethod::isEnabled)
                .orElseThrow(() -> ApiException.notFound("METHOD_NOT_FOUND", "该收款方式暂不可用"));
        if (!price.getCurrency().equals(method.getCurrency())) {
            throw ApiException.badRequest("CURRENCY_MISMATCH",
                    method.getDisplayName() + " 只能支付 " + method.getCurrency() + " 价格");
        }
        Tenant tenant = checkCanOrder(tenantId, userId, price);
        Instant now = Instant.now();
        PaymentOrder order = new PaymentOrder(tenantId, orderNo(now, tenant.getTimezone()), uniqueReference(), price, method,
                now.plus(PAY_WINDOW), userId);
        return orders.saveAndFlush(order);
    }

    /** 在线支付：创建付款单（30 分钟内有效）；付款页由渠道创建，见 OnlinePaymentService。 */
    @Transactional
    public PaymentOrder createOnline(Long tenantId, Long userId, Long priceId, String channel) {
        PlanPrice price = prices.findById(priceId).filter(PlanPrice::isEnabled)
                .orElseThrow(() -> ApiException.notFound("PRICE_NOT_FOUND", "该套餐暂不可购买"));
        Tenant tenant = checkCanOrder(tenantId, userId, price);
        Instant now = Instant.now();
        return orders.saveAndFlush(PaymentOrder.online(tenantId, orderNo(now, tenant.getTimezone()), uniqueReference(),
                price, channel, now.plus(ONLINE_PAY_WINDOW), userId));
    }

    @Transactional
    public void setCheckout(Long id, String checkoutRef) {
        orders.findById(id).ifPresent(o -> o.setCheckoutRef(checkoutRef));
    }

    @Transactional
    public void cancelSilently(Long id) {
        orders.findByIdForUpdate(id).filter(o -> o.getStatus() == PaymentOrder.Status.AWAITING_PAYMENT)
                .ifPresent(PaymentOrder::cancel);
    }

    /** 处理结果（写入付款通知记录）。 */
    public enum PaidResult { GRANTED, GRANTED_WITH_NOTE, DUPLICATE, ANOMALY, UNKNOWN_ORDER }

    /**
     * 渠道确认的一笔付款：校验后开通。付款单行锁 + 渠道交易号唯一，保证同一笔付款只开通一次
     * （重复通知、通知和对账同时到达都安全）。不能开通的情况记为异常，由平台人工处理（多为退款）。
     */
    @Transactional
    public PaidResult applyOnlinePayment(String channel, com.smallbiz.billing.online.PaymentGateway.Paid paid) {
        PaymentOrder o = orders.findByOrderNoForUpdate(paid.orderNo()).orElse(null);
        if (o == null || !channel.equals(o.getChannel())) {
            return PaidResult.UNKNOWN_ORDER;
        }
        if (paid.txnId().equals(o.getProviderTxnId())) {
            return PaidResult.DUPLICATE;
        }
        if (o.getProviderTxnId() != null) {
            // 同一张付款单收到第二笔不同的付款：不再开通，提示平台退款
            o.addAnomaly("重复付款：又收到一笔 " + paid.currency() + " " + paid.amount().toPlainString() + "（交易号 "
                    + paid.txnId() + "），请退款");
            return PaidResult.ANOMALY;
        }
        if (paid.amount().compareTo(o.getAmount()) != 0 || !o.getCurrency().equalsIgnoreCase(paid.currency())) {
            o.markPaidWithProblem(paid.txnId(), paid.paidAt(), "金额不符：收到 " + paid.currency() + " "
                    + paid.amount().toPlainString() + "，应付 " + o.getCurrency() + " " + o.getAmount().toPlainString() + "，未开通");
            return PaidResult.ANOMALY;
        }
        // 开通前先检查（不在 grant 里抛异常：那样整个事务回滚，付款记录也丢了）
        Tenant t = tenants.findByIdForUpdate(o.getTenantId()).orElseThrow(); // 锁定店铺，检查和开通之间不会被其他开通插队
        boolean trial = o.getPeriod() == PlanPrice.Period.TRIAL_MONTH;
        Instant now = Instant.now();
        if (o.isLifetime()) {
            if (o.getCreatedBy() == null || membership.isLifetime(o.getCreatedBy())) {
                o.markPaidWithProblem(paid.txnId(), paid.paidAt(), "已经是高级永久会员，未重复开通，请退款");
                return PaidResult.ANOMALY;
            }
        } else if (t.isLifetime()) {
            o.markPaidWithProblem(paid.txnId(), paid.paidAt(), "已经是高级永久会员，未开通，请退款");
            return PaidResult.ANOMALY;
        } else if (trial && t.isTrialUsed()) {
            o.markPaidWithProblem(paid.txnId(), paid.paidAt(), "首月体验已经用过，未开通，请退款或改为开通月付");
            return PaidResult.ANOMALY;
        } else if (PlanPolicy.rank(t.effectivePlan(now)) > PlanPolicy.rank(o.getPlan())) {
            o.markPaidWithProblem(paid.txnId(), paid.paidAt(), "店铺当前套餐更高，未开通，请退款");
            return PaidResult.ANOMALY;
        }
        o.expireIfDue(now);
        String note = switch (o.getStatus()) {
            case EXPIRED -> "付款单过期后才付款，已照常开通";
            case CANCELLED -> "付款单取消后才付款，已照常开通";
            default -> null;
        };
        if (o.isLifetime()) {
            membership.grantLifetime(o.getCreatedBy(), PlanChangeLog.Source.ONLINE_PAYMENT, o.getId(), null, null,
                    "在线支付 " + o.getOrderNo());
        } else {
            plans.grant(o.getTenantId(), o.getPlan(), o.getDurationDays(), trial, PlanChangeLog.Source.ONLINE_PAYMENT, null,
                    o.getId(), null, "在线支付 " + o.getOrderNo());
        }
        o.markPaid(paid.txnId(), paid.paidAt(), now, note);
        return note == null ? PaidResult.GRANTED : PaidResult.GRANTED_WITH_NOTE;
    }

    /** 对账候选：最近 2 天创建、还没收到付款的在线付款单。 */
    @Transactional(readOnly = true)
    public List<PaymentOrder> onlineUnpaid() {
        return orders.findOnlineUnpaidSince(List.of(PaymentOrder.Status.AWAITING_PAYMENT, PaymentOrder.Status.EXPIRED,
                PaymentOrder.Status.CANCELLED), Instant.now().minus(Duration.ofDays(2)));
    }

    @Transactional
    public List<PaymentOrder> adminAnomalies() {
        return orders.findTop200ByAnomalyIsNotNullAndAnomalyResolvedAtIsNullOrderByIdDesc();
    }

    @Transactional
    public PaymentOrder resolveAnomaly(Long id) {
        PaymentOrder o = orders.findByIdForUpdate(id).orElseThrow(PaymentService::notFound);
        if (o.getAnomaly() == null) {
            throw ApiException.conflict("NO_ANOMALY", "该付款单没有需要处理的异常");
        }
        o.resolveAnomaly(Instant.now());
        return o;
    }

    /** 下单前检查：不能降级、首月体验每店一次、没有未完成的付款单。锁定店铺行，防止同时下两单。 */
    private Tenant checkCanOrder(Long tenantId, Long userId, PlanPrice price) {
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "店铺不存在"));
        if (price.isLifetime()) {
            if (membership.isLifetime(userId)) {
                throw ApiException.conflict("ALREADY_LIFETIME", "你已经是高级永久会员，无需再购买");
            }
        } else {
            if (tenant.isLifetime()) {
                throw ApiException.conflict("ALREADY_LIFETIME", "已经是高级永久会员，无需再购买");
            }
            // 专业版有效期内不能下单标准版（否则审核通过时会覆盖专业版剩余时间）
            PlanService.checkNotDowngrade(tenant.effectivePlan(Instant.now()), price.getPlan());
            if (price.isTrial() && tenant.isTrialUsed()) {
                throw PlanService.trialUsed();
            }
        }
        Instant now = Instant.now();
        List<PaymentOrder> open = orders.findByTenantIdAndStatusIn(tenantId,
                List.of(PaymentOrder.Status.AWAITING_PAYMENT, PaymentOrder.Status.PENDING_REVIEW));
        for (PaymentOrder o : open) {
            o.expireIfDue(now);
            if (o.isOpen()) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                        o.getStatus() == PaymentOrder.Status.PENDING_REVIEW
                                ? "你有一笔付款正在审核中，请耐心等待"
                                : "你有一笔未完成的付款单，请先完成或取消",
                        java.util.Map.of("paymentOrderId", o.getId()));
            }
        }
        return tenant;
    }

    @Transactional
    public List<PaymentOrder> listMine(Long tenantId) {
        Instant now = Instant.now();
        List<PaymentOrder> list = orders.findTop50ByTenantIdOrderByIdDesc(tenantId);
        list.forEach(o -> o.expireIfDue(now));
        return list;
    }

    @Transactional
    public PaymentOrder getMine(Long tenantId, Long id) {
        PaymentOrder o = loadMine(tenantId, id);
        o.expireIfDue(Instant.now());
        return o;
    }

    @Transactional
    public PaymentOrder submitProof(Long tenantId, Long id, MultipartFile image, String transactionRef, String note) {
        PaymentOrder o = orders.findByIdForUpdate(id).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(PaymentService::notFound);
        if (o.expireIfDue(Instant.now())) {
            throw ApiException.conflict("PAYMENT_EXPIRED", "付款单已过期，请重新创建");
        }
        if (o.getStatus() != PaymentOrder.Status.AWAITING_PAYMENT || o.isOnline()) {
            throw ApiException.conflict("INVALID_STATUS", "该付款单当前不能提交凭证");
        }
        String path = storage.storePrivate("payments/t" + tenantId, image);
        o.submitProof(path, Strings.blankToNull(transactionRef), trim(note, 300), Instant.now());
        try {
            return orders.saveAndFlush(o);
        } catch (DataIntegrityViolationException e) {
            storage.deletePrivateQuietly(path);
            throw ApiException.conflict("TRANSACTION_REF_USED", "该交易单号已被提交过");
        }
    }

    @Transactional
    public PaymentOrder cancelMine(Long tenantId, Long id) {
        PaymentOrder o = orders.findByIdForUpdate(id).filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(PaymentService::notFound);
        o.expireIfDue(Instant.now());
        if (o.getStatus() != PaymentOrder.Status.AWAITING_PAYMENT) {
            throw ApiException.conflict("INVALID_STATUS", "只有待付款的付款单可以取消");
        }
        o.cancel();
        return o;
    }

    @Transactional(readOnly = true)
    public byte[] proofImage(Long tenantIdOrNullForAdmin, Long id) {
        PaymentOrder o = tenantIdOrNullForAdmin == null
                ? orders.findById(id).orElseThrow(PaymentService::notFound)
                : loadMine(tenantIdOrNullForAdmin, id);
        if (o.getProofImagePath() == null) {
            throw ApiException.notFound("FILE_NOT_FOUND", "尚未上传付款截图");
        }
        return storage.readPrivate(o.getProofImagePath());
    }

    public String proofContentType(Long id) {
        return orders.findById(id).map(PaymentOrder::getProofImagePath).map(ImageStorage::contentType)
                .orElse("image/jpeg");
    }

    // ---------------- 平台管理员 ----------------

    @Transactional
    public List<PaymentOrder> adminList(PaymentOrder.Status status) {
        Instant now = Instant.now();
        List<PaymentOrder> list = status == null ? orders.findTop200ByOrderByIdDesc()
                : orders.findTop200ByStatusOrderBySubmittedAtAsc(status);
        list.forEach(o -> o.expireIfDue(now));
        return list;
    }

    /** 审核通过：开通套餐。付款单行锁保证重复点击只开通一次。 */
    @Transactional
    public PaymentOrder approve(Long id, Long adminId) {
        PaymentOrder o = orders.findByIdForUpdate(id).orElseThrow(PaymentService::notFound);
        if (o.getStatus() != PaymentOrder.Status.PENDING_REVIEW) {
            throw ApiException.conflict("INVALID_STATUS", "只有待审核的付款单可以通过");
        }
        if (o.isLifetime()) {
            membership.grantLifetime(o.getCreatedBy(), PlanChangeLog.Source.MANUAL_PAYMENT, o.getId(), null, adminId,
                    "付款单 " + o.getOrderNo());
        } else {
            plans.grant(o.getTenantId(), o.getPlan(), o.getDurationDays(), o.getPeriod() == PlanPrice.Period.TRIAL_MONTH,
                    PlanChangeLog.Source.MANUAL_PAYMENT, null, o.getId(), adminId, "付款单 " + o.getOrderNo());
        }
        o.approve(adminId, Instant.now());
        return o;
    }

    @Transactional
    public PaymentOrder reject(Long id, Long adminId, String reason) {
        PaymentOrder o = orders.findByIdForUpdate(id).orElseThrow(PaymentService::notFound);
        if (o.getStatus() != PaymentOrder.Status.PENDING_REVIEW) {
            throw ApiException.conflict("INVALID_STATUS", "只有待审核的付款单可以拒绝");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "请填写拒绝原因，商户会看到");
        }
        o.reject(adminId, trim(reason, 200), Instant.now());
        return o;
    }

    // ---------------- 内部 ----------------

    private PaymentOrder loadMine(Long tenantId, Long id) {
        return orders.findById(id).filter(o -> o.getTenantId().equals(tenantId)).orElseThrow(PaymentService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("PAYMENT_NOT_FOUND", "付款单不存在");
    }

    /** 单号日期按店铺时区，与其他单据一致。 */
    private String orderNo(Instant now, String timezone) {
        return "PAY-" + LocalDate.ofInstant(now, java.time.ZoneId.of(timezone)).format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + randomChars(6);
    }

    private String uniqueReference() {
        for (int i = 0; i < 20; i++) {
            String ref = "SBZ-" + randomChars(4);
            if (!orders.existsByReferenceCode(ref)) {
                return ref;
            }
        }
        throw new IllegalStateException("无法生成备注码");
    }

    private String randomChars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String trim(String s, int max) {
        String t = Strings.blankToNull(s);
        return t == null ? null : t.length() > max ? t.substring(0, max) : t;
    }
}
