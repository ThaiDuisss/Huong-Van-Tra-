package org.hvtra.catalog.service.impl;

import ch.qos.logback.core.spi.ErrorCodes;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.InternalSupplierLookupResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Supplier;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.SupplierMapper;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.SupplierRepository;
import org.hvtra.catalog.service.SupplierService;
import org.hvtra.commonservice.common_core.domain.constants.ErrorEnum;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hvtra.commonservice.common_core.util.CodeUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository repository;
    private final IngredientRepository ingredientRepository;
    private final SupplierMapper mapper;
    private final BusinessSequenceRepository businessSequenceRepository;

    private String generateSupplierCode(String supplierName) {
        String prefix = CodeUtils.getInitials(supplierName, "NCC");
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("SUPPLIER")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("SUPPLIER");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String code;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            code = String.format("%s-%02d", prefix, nextVal);
        } while (repository.existsBySupplierCode(code));

        businessSequenceRepository.saveAndFlush(seq);
        return code;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> findAll(String search, Status status, UUID ingredientId, Pageable pageable) {
        Specification<Supplier> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("supplierCode")), pattern),
                        cb.like(cb.lower(root.get("contactName")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("taxCode")), pattern)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (ingredientId != null) {
                Join<Supplier, Ingredient> ingredientJoin = root.join("ingredients", JoinType.INNER);
                predicates.add(cb.equal(ingredientJoin.get("id"), ingredientId));
                query.distinct(true);
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var page = repository.findAll(spec, pageable);
        warmUpIngredients(page.getContent());
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse findById(UUID id) {
        Supplier supplier = get(id);
        if (Boolean.TRUE.equals(supplier.getIsDeleted())) {
            throw new ResourceNotFoundException("Supplier", id);
        }
        warmUpIngredients(List.of(supplier));
        return mapper.toResponse(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Supplier> suppliers = repository.findAllById(ids).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .toList();
        warmUpIngredients(suppliers);
        return suppliers.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalSupplierLookupResponse> findAllForLookup() {
        return repository.findAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .map(s -> new InternalSupplierLookupResponse(s.getId(), s.getSupplierCode(), s.getName()))
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

    private void warmUpIngredients(List<Supplier> suppliers) {
        if (suppliers.isEmpty()) {
            return;
        }
        List<UUID> supplierIds = suppliers.stream().map(Supplier::getId).toList();
        repository.fetchIngredientsForSuppliers(supplierIds);
    }

    @Override
    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        return createWithCode(request, null);
    }

    @Override
    @Transactional
    public SupplierResponse createWithCode(SupplierRequest request, String explicitCode) {
        String code = explicitCode != null && !explicitCode.isBlank()
                ? explicitCode.trim()
                : generateSupplierCode(request.name());
        validateCode(code, null);

        Supplier supplier = new Supplier();
        mapper.update(supplier, request);
        supplier.setSupplierCode(code);

        if (request.ingredientIds() != null && !request.ingredientIds().isEmpty()) {
            List<Ingredient> ingredients = ingredientRepository.findAllById(request.ingredientIds());
            supplier.setIngredients(new HashSet<>(ingredients));
        }

        Supplier saved = repository.saveAndFlush(supplier);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = get(id);
        if (Boolean.TRUE.equals(supplier.getIsDeleted())) {
            throw new ResourceNotFoundException("Supplier", id);
        }

        mapper.update(supplier, request);

        if (request.ingredientIds() != null) {
            List<Ingredient> ingredients = ingredientRepository.findAllById(request.ingredientIds());
            supplier.setIngredients(new HashSet<>(ingredients));
        }

        Supplier updated = repository.save(supplier);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(UUID id, Status status) {
        Supplier supplier = get(id);
        if (Boolean.TRUE.equals(supplier.getIsDeleted())) {
            throw new ResourceNotFoundException("Supplier", id);
        }
        supplier.setStatus(status);
        return mapper.toResponse(repository.save(supplier));
    }

    @Override
    @Transactional
    public void addIngredients(UUID supplierId, List<UUID> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) return;
        Supplier supplier = get(supplierId);
        if (Boolean.TRUE.equals(supplier.getIsDeleted())) {
            throw new ResourceNotFoundException("Supplier", supplierId);
        }
        List<Ingredient> ingredientsToAdd = ingredientRepository.findAllById(ingredientIds);
        supplier.getIngredients().addAll(ingredientsToAdd);
        repository.save(supplier);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Supplier supplier = get(id);
        supplier.setIsDeleted(true);
        repository.save(supplier);
    }

    private void validateCode(String code, UUID id) {
        boolean exists = id == null
                ? repository.existsBySupplierCode(code)
                : repository.existsBySupplierCodeAndIdNot(code, id);
        if (exists) {
            throw new AppException(ErrorEnum.SUPPLIER_CODE_EXIST);
        }
    }

    private Supplier get(UUID id) {
        Supplier supplier = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        if (Boolean.TRUE.equals(supplier.getIsDeleted())) {
            throw new ResourceNotFoundException("Supplier", id);
        }
        return supplier;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findIngredientIds(UUID supplierId) {
        return ingredientRepository.findIdsBySupplierId(supplierId);
    }
}
