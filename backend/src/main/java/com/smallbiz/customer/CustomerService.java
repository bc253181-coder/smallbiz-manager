package com.smallbiz.customer;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Strings;
import com.smallbiz.customer.CustomerDtos.CustomerRequest;
import com.smallbiz.customer.CustomerDtos.CustomerView;

@Service
public class CustomerService {

    public record Filter(String q, Customer.Type type, Customer.PriceTier priceTier) {
    }

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerView> list(Filter f, Pageable pageable) {
        Specification<Customer> spec = (root, q, cb) -> cb.conjunction();
        if (f.q() != null && !f.q().isBlank()) {
            String like = Strings.likeContains(f.q());
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(root.get("phone"), like, '\\'),
                    cb.like(cb.lower(root.get("email")), like, '\\')));
        }
        if (f.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("customerType"), f.type()));
        }
        if (f.priceTier() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("priceTier"), f.priceTier()));
        }
        return PageResponse.of(customers.findAll(spec, pageable), CustomerView::of);
    }

    @Transactional(readOnly = true)
    public CustomerView get(Long id) {
        return CustomerView.of(load(id));
    }

    @Transactional
    public CustomerView create(CustomerRequest req, Long userId) {
        Customer c = new Customer(userId);
        apply(c, req);
        return CustomerView.of(customers.saveAndFlush(c));
    }

    @Transactional
    public CustomerView update(Long id, CustomerRequest req) {
        Customer c = load(id);
        if (req.version() == null || req.version() != c.getVersion()) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION", "客户资料已被其他人修改，请刷新后重试");
        }
        apply(c, req);
        return CustomerView.of(customers.saveAndFlush(c));
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete(Instant.now());
    }

    /** 当前店铺的客户；其他店铺或已删除的客户一律 404。 */
    public Customer load(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "客户不存在"));
    }

    private static void apply(Customer c, CustomerRequest req) {
        c.setName(req.name().trim());
        c.setPhone(Strings.blankToNull(req.phone()));
        c.setEmail(Strings.blankToNull(req.email()));
        c.setAddress(Strings.blankToNull(req.address()));
        c.setCustomerType(req.customerType());
        c.setPriceTier(req.priceTier());
        c.setNotes(Strings.blankToNull(req.notes()));
    }
}
