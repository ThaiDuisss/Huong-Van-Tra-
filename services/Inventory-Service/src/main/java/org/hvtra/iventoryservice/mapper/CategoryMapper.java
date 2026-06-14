package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.request.CategoryRequest;
import org.hvtra.iventoryservice.dto.response.CategoryResponse;
import org.hvtra.iventoryservice.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category) {
        Long parentId = category.getParentCategory() == null ? null : category.getParentCategory().getId();
        return new CategoryResponse(category.getId(), parentId, category.getName(), category.getStatus());
    }

    public void update(Category category, CategoryRequest request, Category parent) {
        category.setParentCategory(parent);
        category.setName(request.name().trim());
        category.setStatus(request.status());
    }
}
