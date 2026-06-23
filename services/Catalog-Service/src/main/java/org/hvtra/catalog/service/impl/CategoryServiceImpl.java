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
import org.hvtra.catalog.repository.BusinessSequenceRepository;
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
    private final BusinessSequenceRepository businessSequenceRepository;

    public CategoryServiceImpl(CategoryRepository repository, CategoryMapper mapper, BusinessSequenceRepository businessSequenceRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.businessSequenceRepository = businessSequenceRepository;
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

    @Transactional
    public String generateCategoryCode() {
        org.hvtra.catalog.entity.BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("CATEGORY")
                .orElseThrow(() -> new RuntimeException("Category sequence not initialized"));
        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        businessSequenceRepository.saveAndFlush(seq);
        return String.format("CAT%05d", nextVal);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        
        String code = request.categoryCode();
        if (code == null || code.isBlank()) {
            code = generateCategoryCode();
        } else {
            code = code.trim();
            if (code.length() > 50) {
                throw new BadRequestException("Mã danh mục tối đa 50 ký tự");
            }
            if (repository.existsByCategoryCode(code)) {
                throw new BadRequestException("Mã danh mục đã tồn tại: " + code);
            }
        }
        category.setCategoryCode(code);
        
        mapper.update(category, request, parent(request.parentId(), null));
        return mapper.toResponse(repository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = get(id);
        if (id.equals(request.parentId())) throw new BadRequestException("Category cannot be its own parent");
        
        String code = request.categoryCode();
        if (code != null && !code.isBlank()) {
            code = code.trim();
            if (code.length() > 50) {
                throw new BadRequestException("Mã danh mục tối đa 50 ký tự");
            }
            if (!code.equalsIgnoreCase(category.getCategoryCode())) {
                if (repository.existsByCategoryCode(code)) {
                    throw new BadRequestException("Mã danh mục đã tồn tại: " + code);
                }
                category.setCategoryCode(code);
            }
        }
        
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

