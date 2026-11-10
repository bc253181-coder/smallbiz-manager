package com.smallbiz.billing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.tenant.Plan;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

import jakarta.persistence.EntityManager;

/**
 * 发码台（docs/17）：按小红书订单取出还没发过的高级永久会员激活码，记录去向；查看原码、按订单作废、库存。
 * 只供平台管理员使用（Controller 上限制）。每次取码 / 查看 / 作废都写操作记录。
 */
@Service
public class CodeDispenserService {

    /** 可发出的码少于这个数时提醒补充。 */
    public static final int LOW_STOCK = 10;
    public static final int MAX_PER_ORDER = 20;
    /** 快速发码页取出的码记在这个「订单号」下，发码台可以按它搜索。 */
    public static final String QUICK_ORDER = "快速发码";
    static final Long QUICK_OPERATOR = 0L;
    static final String QUICK_REFILL_BATCH = "快速发码自动补充";
    static final int QUICK_REFILL_SIZE = 50;

    public enum CodeState { UNUSED, DISPENSED, REDEEMED, VOIDED }

    public record Dispensed(Long id, String code, String hint) {
    }

    public record Row(Long id, String hint, String batchName, CodeState state, boolean hasRaw, String orderRef,
            Instant dispensedAt, String dispensedByName, Instant redeemedAt, String redeemedTenantName) {
    }

    public record Stock(boolean keyConfigured, long available, int lowStockThreshold, boolean low) {
    }

    public record VoidResult(int voided, List<String> alreadyRedeemed) {
    }

    private final EntityManager em;
    private final ActivationCodeRepository codes;
    private final ActivationCodeBatchRepository batches;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final CodeCrypto crypto;
    private final ActivationService activation;

    public CodeDispenserService(EntityManager em, ActivationCodeRepository codes, ActivationCodeBatchRepository batches,
            UserRepository users, TenantRepository tenants, CodeCrypto crypto, ActivationService activation) {
        this.em = em;
        this.activation = activation;
        this.codes = codes;
        this.batches = batches;
        this.users = users;
        this.tenants = tenants;
        this.crypto = crypto;
    }

    private static final String AVAILABLE_WHERE = """
            FROM activation_codes c JOIN activation_code_batches b ON b.id = c.batch_id
            WHERE c.status = 'UNUSED' AND c.dispensed_at IS NULL AND c.code_encrypted IS NOT NULL
              AND b.lifetime = TRUE AND (b.expires_at IS NULL OR b.expires_at > UTC_TIMESTAMP(6))
            """;

    @Transactional(readOnly = true)
    public Stock stock() {
        long n = ((Number) em.createNativeQuery("SELECT COUNT(*) " + AVAILABLE_WHERE).getSingleResult()).longValue();
        return new Stock(crypto.available(), n, LOW_STOCK, n < LOW_STOCK);
    }

    /**
     * 按订单取码。同一订单已经发过时，需要 confirmRepeat 才再发（防止重复发货）。
     *
     * 抢码方式：先不加锁地读出候选码，再逐个用「UPDATE ... WHERE 还没发出」认领，影响 1 行才算拿到。
     * 同一个码只有一个请求能认领成功（另一个请求等前者提交后重新判断条件，得到 0 行），所以绝不会重复发；
     * 不用 SELECT ... FOR UPDATE：带排序时 MySQL 会先锁住所有符合条件的行，其他请求就全被挡住了。
     * 可发的码不够时抛出异常，整个事务回滚，一个也不发。
     */
    @Transactional
    public List<Dispensed> dispense(String orderRef, int quantity, boolean confirmRepeat, Long adminId) {
        requireKey();
        String order = normalizeOrder(orderRef);
        if (quantity < 1 || quantity > MAX_PER_ORDER) {
            throw ApiException.badRequest("INVALID_QUANTITY", "每次取码 1–" + MAX_PER_ORDER + " 个");
        }
        long already = codes.countByOrderRef(order);
        if (already > 0 && !confirmRepeat) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_DISPENSED",
                    "订单「" + order + "」已经发过 " + already + " 个码，确认要再发吗？", Map.of("count", already));
        }
        return claim(order, quantity, adminId);
    }

    /**
     * 快速发码页（docs/18）：不需要订单号，取 1 个可发出的永久会员激活码；库存用完时自动补充一批再取。
     * 记录的订单号固定为「快速发码」，操作人为 0（不登录使用）。
     */
    @Transactional
    public Dispensed quickIssue() {
        requireKey();
        if (stock().available() == 0) {
            activation.createBatch(new ActivationService.BatchRequest(QUICK_REFILL_BATCH, Plan.PRO, 0, false, true,
                    QUICK_REFILL_SIZE, null), null);
        }
        return claim(QUICK_ORDER, 1, QUICK_OPERATOR).getFirst();
    }

    private List<Dispensed> claim(String order, int quantity, Long adminId) {
        Instant now = Instant.now();
        List<Long> claimed = new ArrayList<>();
        Set<Long> tried = new java.util.HashSet<>();
        while (claimed.size() < quantity) {
            @SuppressWarnings("unchecked")
            List<Number> candidates = em.createNativeQuery("SELECT c.id " + AVAILABLE_WHERE + " ORDER BY c.id LIMIT 50")
                    .getResultList();
            List<Long> fresh = candidates.stream().map(Number::longValue).filter(tried::add).toList();
            if (fresh.isEmpty()) {
                break;
            }
            for (Long id : fresh) {
                int won = em.createNativeQuery("UPDATE activation_codes SET order_ref = :o, dispensed_at = :t, dispensed_by = :u "
                        + "WHERE id = :id AND status = 'UNUSED' AND dispensed_at IS NULL")
                        .setParameter("o", order).setParameter("t", now).setParameter("u", adminId).setParameter("id", id)
                        .executeUpdate();
                if (won == 1) {
                    claimed.add(id);
                    if (claimed.size() == quantity) {
                        break;
                    }
                }
            }
        }
        if (claimed.size() < quantity) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_CODES",
                    "可发出的激活码不足（还有 " + claimed.size() + " 个），请先生成一批高级永久会员激活码",
                    Map.of("available", claimed.size()));
        }
        List<Dispensed> out = new ArrayList<>();
        for (Long id : claimed) {
            Object[] row = (Object[]) em.createNativeQuery("SELECT code_encrypted, code_hint FROM activation_codes WHERE id = :id")
                    .setParameter("id", id).getSingleResult();
            log(id, "DISPENSE", order, adminId);
            out.add(new Dispensed(id, crypto.decrypt((byte[]) row[0]), (String) row[1]));
        }
        return out;
    }
    /** 再次查看原码（例如买家说消息丢了）：记录查看。 */
    @Transactional
    public Dispensed reveal(Long codeId, Long adminId) {
        requireKey();
        ActivationCode c = codes.findById(codeId).orElseThrow(() -> ApiException.notFound("CODE_NOT_FOUND", "激活码不存在"));
        if (c.getCodeEncrypted() == null) {
            throw ApiException.conflict("CODE_NOT_STORED", "这是以前生成的激活码，系统没有保存原码");
        }
        String raw = crypto.decrypt(c.getCodeEncrypted());
        log(c.getId(), "REVEAL", c.getOrderRef(), adminId);
        return new Dispensed(c.getId(), raw, c.getCodeHint());
    }

    /** 退款：作废这个订单还没兑换的码；已兑换的列出来（需要到「店铺」页取消永久会员）。 */
    @Transactional
    public VoidResult voidOrder(String orderRef, Long adminId) {
        String order = normalizeOrder(orderRef);
        List<ActivationCode> list = codes.findByOrderRefForUpdate(order);
        if (list.isEmpty()) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "没有找到这个订单发出的激活码");
        }
        int voided = 0;
        List<String> redeemed = new ArrayList<>();
        for (ActivationCode c : list) {
            if (c.getStatus() == ActivationCode.Status.UNUSED) {
                c.voidCode();
                log(c.getId(), "VOID", order, adminId);
                voided++;
            } else if (c.getStatus() == ActivationCode.Status.REDEEMED) {
                redeemed.add("****" + c.getCodeHint());
            }
        }
        return new VoidResult(voided, redeemed);
    }

    /**
     * 搜索：订单号（包含）或激活码后 4 位；不填时显示最近发出的。只列出永久会员激活码。
     */
    @Transactional(readOnly = true)
    public List<Row> search(String q) {
        String query = q == null ? "" : q.trim();
        List<ActivationCode> found;
        if (query.isEmpty()) {
            found = codes.findTop100ByDispensedAtIsNotNullOrderByDispensedAtDescIdDesc();
        } else {
            String hint = query.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            found = codes.searchDispenser(query, hint.length() >= 4 ? hint.substring(hint.length() - 4) : "~");
        }
        Map<Long, ActivationCodeBatch> batchById = batches.findAllById(found.stream().map(ActivationCode::getBatchId).distinct().toList())
                .stream().collect(Collectors.toMap(ActivationCodeBatch::getId, Function.identity()));
        Set<Long> userIds = found.stream().map(ActivationCode::getDispensedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        Set<Long> tenantIds = found.stream().map(ActivationCode::getRedeemedTenantId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> tenantNames = tenants.findAllById(tenantIds).stream().collect(Collectors.toMap(Tenant::getId, Tenant::getName));
        return found.stream().filter(c -> {
            ActivationCodeBatch b = batchById.get(c.getBatchId());
            return b != null && b.isLifetime();
        }).map(c -> new Row(c.getId(), c.getCodeHint(), batchById.get(c.getBatchId()).getName(), state(c),
                c.getCodeEncrypted() != null, c.getOrderRef(), c.getDispensedAt(), names.get(c.getDispensedBy()),
                c.getRedeemedAt(), tenantNames.get(c.getRedeemedTenantId()))).toList();
    }

    static CodeState state(ActivationCode c) {
        return switch (c.getStatus()) {
            case REDEEMED -> CodeState.REDEEMED;
            case VOIDED -> CodeState.VOIDED;
            case UNUSED -> c.getDispensedAt() != null ? CodeState.DISPENSED : CodeState.UNUSED;
        };
    }

    private void log(Long codeId, String action, String orderRef, Long adminId) {
        em.createNativeQuery("INSERT INTO code_dispense_logs (code_id, action, order_ref, operator_id, created_at) "
                + "VALUES (:c, :a, :o, :u, UTC_TIMESTAMP(6))").setParameter("c", codeId).setParameter("a", action)
                .setParameter("o", orderRef).setParameter("u", adminId).executeUpdate();
    }

    private void requireKey() {
        if (!crypto.available()) {
            throw ApiException.conflict("CODE_KEY_MISSING", "发码台需要先在服务器配置 CODE_ENCRYPTION_KEY");
        }
    }

    private static String normalizeOrder(String orderRef) {
        String s = orderRef == null ? "" : orderRef.trim();
        if (s.isEmpty()) {
            throw ApiException.badRequest("ORDER_REQUIRED", "请填写小红书订单号");
        }
        if (s.length() > 100) {
            throw ApiException.badRequest("ORDER_TOO_LONG", "订单号最多 100 个字符");
        }
        return s;
    }
}
