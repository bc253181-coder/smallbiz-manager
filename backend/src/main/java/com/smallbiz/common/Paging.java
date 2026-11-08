package com.smallbiz.common;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** 解析 page / size / sort 参数；排序字段只允许白名单内的值，防止按任意字段排序。 */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /**
     * @param sort        形如 "name,asc"；为空时使用默认排序
     * @param allowed     对外排序字段名 → 实体属性名
     * @param defaultSort 默认排序
     */
    public static Pageable of(Integer page, Integer size, String sort, Map<String, String> allowed, Sort defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        Sort order = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = allowed.get(parts[0].trim());
            if (property == null) {
                throw ApiException.badRequest("INVALID_SORT", "不支持的排序字段：" + parts[0].trim());
            }
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
            order = Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, property).and(Sort.by("id").descending());
        }
        return PageRequest.of(p, s, order);
    }
}
