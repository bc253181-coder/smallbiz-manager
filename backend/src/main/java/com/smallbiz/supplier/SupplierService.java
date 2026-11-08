package com.smallbiz.supplier;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.common.ApiException;
import com.smallbiz.common.PageResponse;
import com.smallbiz.common.Strings;
import com.smallbiz.supplier.SupplierDtos.SupplierRequest;
import com.smallbiz.supplier.SupplierDtos.SupplierView;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierView> list(String q, Pageable pageable) {
        Specification<Supplier> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String like = Strings.likeContains(q);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("contactPerson")), like, '\\'),
                    cb.like(root.get("phone"), like, '\\'),
                    cb.like(cb.lower(root.get("suppliedProductsNote")), like, '\\')));
        }
        return PageResponse.of(suppliers.findAll(spec, pageable), SupplierView::of);
    }

    @Transactional(readOnly = true)
    public SupplierView get(Long id) {
        return SupplierView.of(load(id));
    }

    @Transactional
    public SupplierView create(SupplierRequest req, Long userId) {
        Supplier s = new Supplier(userId);
        apply(s, req);
        return SupplierView.of(suppliers.saveAndFlush(s));
    }

    @Transactional
    public SupplierView update(Long id, SupplierRequest req) {
        Supplier s = load(id);
        if (req.version() == null || req.version() != s.getVersion()) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION", "供应商资料已被其他人修改，请刷新后重试");
        }
        apply(s, req);
        return SupplierView.of(suppliers.saveAndFlush(s));
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete(Instant.now());
    }

    /** 当前店铺的供应商；其他店铺或已删除的一律 404。 */
    public Supplier load(Long id) {
        return suppliers.findById(id)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "供应商不存在"));
    }

    private static void apply(Supplier s, SupplierRequest req) {
        s.setName(req.name().trim());
        s.setContactPerson(Strings.blankToNull(req.contactPerson()));
        s.setPhone(Strings.blankToNull(req.phone()));
        s.setEmail(Strings.blankToNull(req.email()));
        s.setAddress(Strings.blankToNull(req.address()));
        s.setSuppliedProductsNote(Strings.blankToNull(req.suppliedProductsNote()));
        s.setNotes(Strings.blankToNull(req.notes()));
    }
}
