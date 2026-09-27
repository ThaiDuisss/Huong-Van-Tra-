package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category) {
        if (category == null) return null;
        UUID parentId = category.getParentCategory() == null ? null : category.getParentCategory().getId();
        return new CategoryResponse(category.getId(), parentId, category.getName(), category.getStatus(), category.getCategoryCode(), category.getDefaultReorderPoint(), null);
    }

    /** Kèm tên danh mục cha — chỉ gọi khi parentCategory đã được fetch sẵn (tránh lazy-load từng dòng). */
    public CategoryResponse toResponseWithParentName(Category category) {
        if (category == null) return null;
        Category parent = category.getParentCategory();
        return new CategoryResponse(category.getId(), parent == null ? null : parent.getId(), category.getName(),
                category.getStatus(), category.getCategoryCode(), category.getDefaultReorderPoint(),
                parent == null ? null : parent.getName());
    }


    public void update(Category category, CategoryRequest request, Category parent) {
        category.setParentCategory(parent);
        category.setName(request.name().trim());
        category.setStatus(request.status());
        category.setDefaultReorderPoint(request.defaultReorderPoint());
    }
}

