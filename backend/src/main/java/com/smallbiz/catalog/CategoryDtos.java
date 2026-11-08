package com.smallbiz.catalog;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "请填写分类名称") @Size(max = 50, message = "分类名称最多 50 字") String name,
            Long parentId,
            @Min(value = 0, message = "排序不能为负数") @Max(value = 9999, message = "排序最大 9999") Integer sortOrder) {
    }

    public record UpdateCategoryRequest(
            @NotBlank(message = "请填写分类名称") @Size(max = 50, message = "分类名称最多 50 字") String name,
            @Min(value = 0, message = "排序不能为负数") @Max(value = 9999, message = "排序最大 9999") Integer sortOrder) {
    }

    /** 分类树节点；productCount 为直接属于该分类的商品数。 */
    public record CategoryNode(Long id, String name, Long parentId, int sortOrder, long productCount,
            List<CategoryNode> children) {
    }
}
