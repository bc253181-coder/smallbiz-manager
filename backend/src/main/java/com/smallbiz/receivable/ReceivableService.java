package com.smallbiz.receivable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.auth.User;
import com.smallbiz.auth.UserRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Strings;
import com.smallbiz.customer.Customer;
import com.smallbiz.customer.CustomerRepository;
import com.smallbiz.customer.CustomerService;
import com.smallbiz.receivable.ReceivableDtos.Aging;
import com.smallbiz.receivable.ReceivableDtos.AllocationView;
import com.smallbiz.receivable.ReceivableDtos.CustomerReceivable;
import com.smallbiz.receivable.ReceivableDtos.CustomerRow;
import com.smallbiz.receivable.ReceivableDtos.LineType;
import com.smallbiz.receivable.ReceivableDtos.OpenOrder;
import com.smallbiz.receivable.ReceivableDtos.PaymentRequest;
import com.smallbiz.receivable.ReceivableDtos.PaymentView;
import com.smallbiz.receivable.ReceivableDtos.Statement;
import com.smallbiz.receivable.ReceivableDtos.StatementLine;
import com.smallbiz.receivable.ReceivableDtos.Summary;
import com.smallbiz.sales.SalesOrder;
import com.smallbiz.sales.SalesOrderRepository;
import com.smallbiz.tenant.SequenceService;
import com.smallbiz.tenant.Tenant;
import com.smallbiz.tenant.TenantRepository;

/**
 * 应收账款：收款（先欠先还 / 指定单）、作废、汇总、账龄、对账单。
 *
 * <p>一致性：收款与作废都先对相关销售单加行锁，再修改已收金额；并发收款时后到的请求会等待前一个提交，
 * 然后基于最新的已收金额判断是否超收。所有查询使用自动店铺隔离（JPQL），其他店铺的数据一律不可见。
 */
@Service
public class ReceivableService {

    public record PaymentFilter(Long customerId, LocalDate from, LocalDate to, CustomerPayment.Status status) {
    }

    /** 允许收款时间比服务器时间略晚（客户端时钟误差）。 */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final CustomerPaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final SalesOrderRepository orders;
    private final CustomerService customerService;
    private final CustomerRepository customerRepo;
    private final SequenceService sequences;
    private final TenantRepository tenants;
    private final UserRepository users;

    public ReceivableService(CustomerPaymentRepository payments, PaymentAllocationRepository allocations,
            SalesOrderRepository orders, CustomerService customerService, CustomerRepository customerRepo,
            SequenceService sequences, TenantRepository tenants, UserRepository users) {
        this.payments = payments;
        this.allocations = allocations;
        this.orders = orders;
        this.customerService = customerService;
        this.customerRepo = customerRepo;
        this.sequences = sequences;
        this.tenants = tenants;
        this.users = users;
    }

    // ---------------- 收款 ----------------

    @Transactional
    public PaymentView receive(PaymentRequest req, Long tenantId, Long userId) {
        Customer customer = customerService.load(req.customerId());
        BigDecimal amount = req.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        Instant now = Instant.now();
        Instant receivedAt = req.receivedAt() == null ? now : req.receivedAt();
        if (receivedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw ApiException.badRequest("INVALID_RECEIVED_AT", "收款时间不能晚于现在");
        }

        List<SalesOrder> targets;
        if (req.salesOrderId() != null) {
            SalesOrder o = orders.findByIdForUpdate(req.salesOrderId())
                    .filter(x -> customer.getId().equals(x.getCustomerId()))
                    .orElseThrow(() -> ApiException.notFound("SALE_NOT_FOUND", "该客户没有这张销售单"));
            if (o.getStatus() != SalesOrder.Status.COMPLETED) {
                throw ApiException.conflict("INVALID_STATUS", "已取消的销售单不能收款");
            }
            targets = List.of(o);
        } else {
            targets = orders.findUnpaidByCustomerForUpdate(customer.getId());
        }
        BigDecimal outstanding = targets.stream().map(SalesOrder::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_EXCEEDS_BALANCE",
                    outstanding.signum() == 0 ? "没有需要收款的欠款" : "收款金额不能超过欠款 " + outstanding.toPlainString(),
                    Map.of("outstanding", outstanding.toPlainString()));
        }

        CustomerPayment payment = payments.saveAndFlush(new CustomerPayment(sequences.documentNo(tenantId, "RC"),
                customer.getId(), amount, req.method(), receivedAt, Strings.blankToNull(req.notes()), userId));
        BigDecimal left = amount;
        for (SalesOrder o : targets) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal part = left.min(o.outstanding());
            if (part.signum() <= 0) {
                continue;
            }
            if (receivedAt.isBefore(o.getOrderDate())) {
                throw ApiException.badRequest("INVALID_RECEIVED_AT",
                        "收款时间不能早于销售单 " + o.getOrderNo() + " 的开单时间");
            }
            o.applyPayment(part, receivedAt);
            allocations.save(new PaymentAllocation(payment.getId(), o.getId(), part));
            left = left.subtract(part);
        }
        orders.flush();
        return toView(payment);
    }

    /** 作废收款：撤回冲抵金额，相关销售单恢复为未结清。 */
    @Transactional
    public PaymentView voidPayment(Long id, String reason, Long userId) {
        CustomerPayment payment = payments.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "收款记录不存在"));
        if (payment.getStatus() == CustomerPayment.Status.VOIDED) {
            throw ApiException.conflict("PAYMENT_ALREADY_VOIDED", "这笔收款已经作废");
        }
        List<PaymentAllocation> allocs = allocations.findByPaymentIdOrderByIdAsc(id);
        Map<Long, SalesOrder> locked = orders.findAllByIdForUpdate(allocs.stream().map(PaymentAllocation::getSalesOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(SalesOrder::getId, Function.identity()));
        payment.markVoided(Instant.now(), userId, reason.trim());
        payments.saveAndFlush(payment);
        for (PaymentAllocation a : allocs) {
            SalesOrder o = locked.get(a.getSalesOrderId());
            o.reversePayment(a.getAmount(), allocations.lastValidReceivedAt(o.getId()));
        }
        orders.flush();
        return toView(payment);
    }

    @Transactional(readOnly = true)
    public PaymentView get(Long id) {
        return toView(payments.findById(id).orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "收款记录不存在")));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentView> list(Long tenantId, PaymentFilter f, Pageable pageable) {
        ZoneId zone = zone(tenantId);
        Specification<CustomerPayment> spec = (root, q, cb) -> cb.conjunction();
        if (f.customerId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customerId"), f.customerId()));
        }
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.from() != null) {
            Instant start = f.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("receivedAt"), start));
        }
        if (f.to() != null) {
            Instant end = f.to().plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("receivedAt"), end));
        }
        var page = payments.findAll(spec, pageable);
        Map<Long, List<PaymentAllocation>> allocsByPayment = allocations
                .findByPaymentIdIn(page.getContent().stream().map(CustomerPayment::getId).toList())
                .stream().collect(Collectors.groupingBy(PaymentAllocation::getPaymentId));
        Lookups lk = lookups(page.getContent(), allocsByPayment.values().stream().flatMap(List::stream).toList());
        return PageResponse.of(page, p -> toView(p, allocsByPayment.getOrDefault(p.getId(), List.of()), lk));
    }

    // ---------------- 汇总与账龄 ----------------

    @Transactional(readOnly = true)
    public Summary summary(Long tenantId) {
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(zone);
        List<SalesOrder> open = orders.findAllUnpaid();
        Aging aging = aging(open, today);
        BigDecimal total = sum(open.stream().map(SalesOrder::outstanding));
        BigDecimal overdue = sum(open.stream().filter(o -> ageDays(o, zone, today) > 30).map(SalesOrder::outstanding));
        long customers = open.stream().map(SalesOrder::getCustomerId).distinct().count();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        return new Summary(total, customers, open.size(), overdue, aging, payments.sumValidSince(monthStart));
    }

    /** 有欠款的客户，按欠款金额从高到低。minAgeDays：只看有超过该天数的欠款单的客户。 */
    @Transactional(readOnly = true)
    public List<CustomerRow> customers(Long tenantId, String q, Integer minAgeDays) {
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(zone);
        Map<Long, List<SalesOrder>> byCustomer = orders.findAllUnpaid().stream()
                .collect(Collectors.groupingBy(SalesOrder::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Customer> customers = customerRepo.findAllById(byCustomer.keySet()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<CustomerRow> rows = new ArrayList<>();
        byCustomer.forEach((customerId, list) -> {
            Customer c = customers.get(customerId);
            String name = c != null ? c.getName() : list.get(0).getCustomerName();
            String phone = c != null ? c.getPhone() : null;
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query) && (phone == null || !phone.contains(query))) {
                return;
            }
            SalesOrder oldest = list.get(0);
            long oldestDays = ageDays(oldest, zone, today);
            if (minAgeDays != null && oldestDays <= minAgeDays) {
                return;
            }
            rows.add(new CustomerRow(customerId, name, phone, sum(list.stream().map(SalesOrder::outstanding)), list.size(),
                    oldest.getOrderDate(), oldestDays, aging(list, today)));
        });
        rows.sort(Comparator.comparing(CustomerRow::outstanding).reversed().thenComparing(CustomerRow::customerId));
        return rows;
    }

    /** 某客户的欠款与未结清销售单（开单页、收款弹窗、客户详情使用）。 */
    @Transactional(readOnly = true)
    public CustomerReceivable customer(Long tenantId, Long customerId) {
        Customer c = customerService.load(customerId);
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(zone);
        List<SalesOrder> open = orders.findAllUnpaid().stream().filter(o -> customerId.equals(o.getCustomerId())).toList();
        List<OpenOrder> rows = open.stream().map(o -> new OpenOrder(o.getId(), o.getOrderNo(), o.getOrderDate(),
                o.getTotalAmount(), o.getPaidAmount(), o.outstanding(), o.getPaymentStatus(), ageDays(o, zone, today))).toList();
        return new CustomerReceivable(c.getId(), c.getName(), sum(open.stream().map(SalesOrder::outstanding)),
                aging(open, today), rows);
    }

    // ---------------- 对账单 ----------------

    /**
     * 对账单：期初欠款 + 期间新增欠款 − 期间收款 = 期末欠款。
     * 欠款按"当前有效"的数据计算：已取消的销售单、已作废的收款不计入。
     */
    @Transactional(readOnly = true)
    public Statement statement(Long tenantId, Long customerId, LocalDate from, LocalDate to) {
        Customer c = customerService.load(customerId);
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        ZoneId zone = ZoneId.of(tenant.getTimezone());
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw ApiException.badRequest("DATE_RANGE_TOO_LONG", "对账单最长一年");
        }
        Instant startAt = start.atStartOfDay(zone).toInstant();
        Instant endAt = end.plusDays(1).atStartOfDay(zone).toInstant();

        List<SalesOrder> sales = orders.findByCustomerIdAndStatusAndOrderDateLessThanOrderByOrderDateAscIdAsc(customerId,
                SalesOrder.Status.COMPLETED, endAt);
        List<CustomerPayment> paid = payments.findByCustomerIdAndStatusAndReceivedAtLessThanOrderByReceivedAtAscIdAsc(
                customerId, CustomerPayment.Status.VALID, endAt);

        BigDecimal opening = sum(sales.stream().filter(o -> o.getOrderDate().isBefore(startAt)).map(ReceivableService::credited))
                .subtract(sum(paid.stream().filter(p -> p.getReceivedAt().isBefore(startAt)).map(CustomerPayment::getAmount)));

        record Raw(Instant date, int order, StatementLine line) {
        }
        List<Raw> raws = new ArrayList<>();
        for (SalesOrder o : sales) {
            if (!o.getOrderDate().isBefore(startAt)) {
                raws.add(new Raw(o.getOrderDate(), 0, new StatementLine(o.getOrderDate(), LineType.SALE, o.getId(),
                        o.getOrderNo(), "销售", o.getTotalAmount(), o.getPaidAtSale(), credited(o), BigDecimal.ZERO, null)));
            }
        }
        for (CustomerPayment p : paid) {
            if (!p.getReceivedAt().isBefore(startAt)) {
                raws.add(new Raw(p.getReceivedAt(), 1, new StatementLine(p.getReceivedAt(), LineType.PAYMENT, p.getId(),
                        p.getPaymentNo(), "收款 · " + methodLabel(p.getMethod()), null, null, BigDecimal.ZERO,
                        p.getAmount(), null)));
            }
        }
        raws.sort(Comparator.comparing(Raw::date).thenComparing(Raw::order));
        List<StatementLine> lines = new ArrayList<>();
        BigDecimal balance = opening;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal salesTotal = BigDecimal.ZERO;
        for (Raw r : raws) {
            StatementLine l = r.line();
            balance = balance.add(l.debit()).subtract(l.credit());
            debit = debit.add(l.debit());
            credit = credit.add(l.credit());
            if (l.amount() != null) {
                salesTotal = salesTotal.add(l.amount());
            }
            lines.add(new StatementLine(l.date(), l.type(), l.refId(), l.docNo(), l.description(), l.amount(),
                    l.paidAtSale(), l.debit(), l.credit(), balance));
        }
        return new Statement(c.getId(), c.getName(), c.getPhone(), tenant.getName(), tenant.getContactPhone(),
                tenant.getAddress(), tenant.getCurrency(), start, end, opening, salesTotal, debit, credit, balance, lines,
                Instant.now());
    }

    // ---------------- 内部 ----------------

    /** 销售单形成的欠款（应收 − 开单时实收）。 */
    private static BigDecimal credited(SalesOrder o) {
        return o.getTotalAmount().subtract(o.getPaidAtSale());
    }

    private static long ageDays(SalesOrder o, ZoneId zone, LocalDate today) {
        return ChronoUnit.DAYS.between(LocalDate.ofInstant(o.getOrderDate(), zone), today);
    }

    private Aging aging(List<SalesOrder> list, LocalDate today) {
        ZoneId zone = list.isEmpty() ? ZoneId.of("UTC") : zone(list.get(0).getTenantId());
        BigDecimal[] b = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        for (SalesOrder o : list) {
            long d = ageDays(o, zone, today);
            int i = d <= 30 ? 0 : d <= 60 ? 1 : d <= 90 ? 2 : 3;
            b[i] = b[i].add(o.outstanding());
        }
        return new Aging(b[0], b[1], b[2], b[3]);
    }

    private static BigDecimal sum(Stream<BigDecimal> s) {
        return s.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ZoneId zone(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getTimezone).map(ZoneId::of).orElse(ZoneId.of("Asia/Shanghai"));
    }

    public static String methodLabel(CustomerPayment.Method m) {
        return switch (m) {
            case CASH -> "现金";
            case WECHAT -> "微信";
            case ALIPAY -> "支付宝";
            case BANK -> "银行转账";
            case TNG -> "Touch 'n Go";
            case DUITNOW -> "DuitNow";
            case OTHER -> "其他";
        };
    }

    private record Lookups(Map<Long, String> userNames, Map<Long, String> customerNames, Map<Long, SalesOrder> orders) {
    }

    private Lookups lookups(List<CustomerPayment> list, List<PaymentAllocation> allocs) {
        Set<Long> userIds = list.stream().flatMap(p -> Stream.of(p.getCreatedBy(), p.getVoidedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNames = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<Long, String> customerNames = customerRepo.findAllById(list.stream().map(CustomerPayment::getCustomerId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Customer::getId, Customer::getName));
        Map<Long, SalesOrder> orderMap = orders.findAllById(allocs.stream().map(PaymentAllocation::getSalesOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(SalesOrder::getId, Function.identity()));
        return new Lookups(userNames, customerNames, orderMap);
    }

    private PaymentView toView(CustomerPayment p) {
        List<PaymentAllocation> allocs = allocations.findByPaymentIdOrderByIdAsc(p.getId());
        return toView(p, allocs, lookups(List.of(p), allocs));
    }

    private static PaymentView toView(CustomerPayment p, List<PaymentAllocation> allocs, Lookups lk) {
        List<AllocationView> av = allocs.stream().map(a -> {
            SalesOrder o = lk.orders().get(a.getSalesOrderId());
            return new AllocationView(a.getSalesOrderId(), o == null ? null : o.getOrderNo(),
                    o == null ? null : o.getOrderDate(), a.getAmount());
        }).toList();
        return new PaymentView(p.getId(), p.getPaymentNo(), p.getCustomerId(), lk.customerNames().get(p.getCustomerId()),
                p.getAmount(), p.getMethod(), p.getReceivedAt(), p.getNotes(), p.getStatus(), p.getVoidedAt(),
                lk.userNames().get(p.getVoidedBy()), p.getVoidReason(), lk.userNames().get(p.getCreatedBy()),
                p.getCreatedAt(), av);
    }
}
