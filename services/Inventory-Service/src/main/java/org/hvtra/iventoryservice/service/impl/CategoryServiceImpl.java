package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.request.CategoryRequest;
import org.hvtra.iventoryservice.dto.response.CategoryResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.Category;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.CategoryMapper;
import org.hvtra.iventoryservice.repository.CategoryRepository;
import org.hvtra.iventoryservice.service.CategoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

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
    public CategoryResponse findById(Long id) {
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
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = get(id);
        if (id.equals(request.parentId())) throw new BadRequestException("Category cannot be its own parent");
        mapper.update(category, request, parent(request.parentId(), id));
        return mapper.toResponse(repository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(Long id, Status status) {
        Category category = get(id);
        category.setStatus(status);
        return mapper.toResponse(repository.save(category));
    }

    private Category get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private Category parent(Long parentId, Long categoryId) {
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
