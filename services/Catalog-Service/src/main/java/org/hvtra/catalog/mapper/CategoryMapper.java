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
        return new CategoryResponse(category.getId(), parentId, category.getName(), category.getStatus(), category.getCategoryCode(), category.getDefaultReorderPoint());
    }


    public void update(Category category, CategoryRequest request, Category parent) {
        category.setParentCategory(parent);
        category.setName(request.name().trim());
        category.setStatus(request.status());
        category.setDefaultReorderPoint(request.defaultReorderPoint());
    }
}

