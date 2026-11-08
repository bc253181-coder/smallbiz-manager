package com.smallbiz.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smallbiz.catalog.CategoryDtos.CategoryNode;
import com.smallbiz.catalog.CategoryDtos.CreateCategoryRequest;
import com.smallbiz.catalog.CategoryDtos.UpdateCategoryRequest;
import com.smallbiz.common.ApiException;

@Service
public class CategoryService {

    /** 防止滥用：每个店铺最多的分类数。 */
    static final int MAX_CATEGORIES = 500;

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Long> counts = products.countByCategory().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, List<Category>> byParent = all.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryNode> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null) {
                List<CategoryNode> children = byParent.getOrDefault(c.getId(), List.of()).stream()
                        .map(ch -> node(ch, counts, List.of())).toList();
                roots.add(node(c, counts, children));
            }
        }
        return roots;
    }

    private static CategoryNode node(Category c, Map<Long, Long> counts, List<CategoryNode> children) {
        return new CategoryNode(c.getId(), c.getName(), c.getParentId(), c.getSortOrder(),
                counts.getOrDefault(c.getId(), 0L), children);
    }

    @Transactional
    public CategoryNode create(CreateCategoryRequest req) {
        if (categories.count() >= MAX_CATEGORIES) {
            throw ApiException.conflict("CATEGORY_LIMIT_REACHED", "分类数量已达上限 " + MAX_CATEGORIES);
        }
        String name = req.name().trim();
        if (req.parentId() != null) {
            Category parent = load(req.parentId());
            if (parent.getParentId() != null) {
                throw ApiException.badRequest("CATEGORY_TOO_DEEP", "最多支持两级分类");
            }
        }
        ensureUniqueName(req.parentId(), name, null);
        Category saved = categories.save(new Category(req.parentId(), name,
                req.sortOrder() == null ? 0 : req.sortOrder()));
        return new CategoryNode(saved.getId(), saved.getName(), saved.getParentId(), saved.getSortOrder(), 0,
                List.of());
    }

    @Transactional
    public void update(Long id, UpdateCategoryRequest req) {
        Category c = load(id);
        String name = req.name().trim();
        ensureUniqueName(c.getParentId(), name, c.getId());
        c.setName(name);
        if (req.sortOrder() != null) {
            c.setSortOrder(req.sortOrder());
        }
    }

    @Transactional
    public void delete(Long id) {
        Category c = load(id);
        if (categories.existsByParentId(c.getId())) {
            throw ApiException.conflict("CATEGORY_HAS_CHILDREN", "请先删除或移动该分类下的子分类");
        }
        if (products.existsByCategoryId(c.getId())) {
            throw ApiException.conflict("CATEGORY_NOT_EMPTY", "该分类下还有商品，请先把商品移到其他分类");
        }
        // 已删除（软删除）的商品仍引用该分类，先解除关联，否则外键阻止删除
        products.detachDeletedFromCategory(c.getTenantId(), c.getId());
        categories.delete(c);
    }

    /** 加载当前店铺的分类；其他店铺的分类视为不存在。 */
    public Category load(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "分类不存在"));
    }

    /** 某分类及其子分类的 id（用于按一级分类筛选商品）。 */
    @Transactional(readOnly = true)
    public List<Long> selfAndChildren(Long id) {
        Category c = load(id);
        List<Long> ids = new ArrayList<>();
        ids.add(c.getId());
        categories.findByParentId(c.getId()).forEach(ch -> ids.add(ch.getId()));
        return ids;
    }

    private void ensureUniqueName(Long parentId, String name, Long selfId) {
        var existing = parentId == null
                ? categories.findFirstByParentIdIsNullAndName(name)
                : categories.findFirstByParentIdAndName(parentId, name);
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw ApiException.conflict("CATEGORY_NAME_EXISTS", "同一级下已有同名分类");
        }
    }
}
