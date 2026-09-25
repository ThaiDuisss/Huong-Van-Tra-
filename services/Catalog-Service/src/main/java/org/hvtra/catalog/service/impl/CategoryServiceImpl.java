package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.entity.BusinessSequence;
import org.hvtra.catalog.entity.Category;
import org.hvtra.commonservice.common_core.util.CodeUtils;
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
import java.util.Collections;
import java.util.List;

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
            Class<?> resultType = query.getResultType();
            if (!Long.class.equals(resultType) && !long.class.equals(resultType)) {
                root.fetch("parentCategory", JoinType.LEFT); // tên danh mục cha, không lazy-load từng dòng
            }
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponseWithParentName));
    }

    @Override
    public CategoryResponse findById(UUID id) {
        return mapper.toResponseWithParentName(get(id));
    }

    @Transactional
    public String generateCategoryCode(String categoryName) {
        String prefix = CodeUtils.getInitials(categoryName, "DM");
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("CATEGORY")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("CATEGORY");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String code;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            code = String.format("%s-%02d", prefix, nextVal);
        } while (repository.existsByCategoryCode(code));

        businessSequenceRepository.saveAndFlush(seq);
        return code;
    }

    @Transactional
    public String generateCategoryCode() {
        return generateCategoryCode(null);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        
        String code = request.categoryCode();
        if (code == null || code.isBlank()) {
            code = generateCategoryCode(request.name());
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

    @Override
    public List<CategoryResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllById(ids).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public PageResponse<LookupResponse> lookup(String search, List<UUID> ids, Pageable pageable) {
        if (ids != null && !ids.isEmpty()) {
            List<LookupResponse> content = repository.findLookupByIds(ids).stream()
                    .map(v -> new LookupResponse(v.getId().toString(), v.getName()))
                    .toList();
            return new PageResponse<>(content, 0, content.size(), content.size(), 1, true, true);
        }
        String keyword = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(repository.findLookup(keyword, pageable)
                .map(v -> new LookupResponse(v.getId().toString(), v.getName())));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = get(id);
        if (category.getIsDeleted()) {
            throw new ResourceNotFoundException("Category", id);
        }
        category.setIsDeleted(true);
        repository.save(category);
    }
}

