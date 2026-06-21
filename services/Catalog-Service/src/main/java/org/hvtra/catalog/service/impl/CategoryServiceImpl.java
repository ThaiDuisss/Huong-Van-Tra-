package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.CategoryMapper;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.service.CategoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    public CategoryServiceImpl(CategoryRepository repository, CategoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<CategoryResponse> findAll(String name, Status status, Pageable pageable) {
        Specification<Category> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public CategoryResponse findById(UUID id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        mapper.update(category, request, parent(request.parentId(), null));
        return mapper.toResponse(repository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = get(id);
        if (id.equals(request.parentId())) throw new BadRequestException("Category cannot be its own parent");
        mapper.update(category, request, parent(request.parentId(), id));
        return mapper.toResponse(repository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(UUID id, Status status) {
        Category category = get(id);
        category.setStatus(status);
        return mapper.toResponse(repository.save(category));
    }

    private Category get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private Category parent(UUID parentId, UUID categoryId) {
        if (parentId == null) return null;
        Category parent = get(parentId);
        for (Category current = parent; current != null; current = current.getParentCategory()) {
            if (current.getId().equals(categoryId)) {
                throw new BadRequestException("Category hierarchy cannot contain a cycle");
            }
        }
        return parent;
    }
}

