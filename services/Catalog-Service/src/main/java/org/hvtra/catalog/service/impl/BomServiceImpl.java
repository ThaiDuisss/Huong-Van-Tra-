package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.BomRequest;
import org.hvtra.catalog.dto.response.BomLookupResponse;
import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.*;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.BomMapper;
import org.hvtra.catalog.repository.*;
import org.hvtra.catalog.service.BomService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BomServiceImpl implements BomService {
    private final BomRepository repository;
    private final ProductVariantRepository productVariantRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitRepository unitRepository;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final BomMapper mapper;

    public BomServiceImpl(
            BomRepository repository,
            ProductVariantRepository productVariantRepository,
            IngredientRepository ingredientRepository,
            UnitRepository unitRepository,
            BusinessSequenceRepository businessSequenceRepository,
            BomMapper mapper) {
        this.repository = repository;
        this.productVariantRepository = productVariantRepository;
        this.ingredientRepository = ingredientRepository;
        this.unitRepository = unitRepository;
        this.businessSequenceRepository = businessSequenceRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<BomResponse> findAll(
            UUID categoryId, UUID productVariantId, UUID productId, String search, Integer version, Status status, Pageable pageable) {
        Specification<Bom> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("productVariant", jakarta.persistence.criteria.JoinType.LEFT)
                    .fetch("product", jakarta.persistence.criteria.JoinType.LEFT)
                    .fetch("category", jakarta.persistence.criteria.JoinType.LEFT);
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("productVariant").get("product").get("category").get("id"), categoryId));
            }
            if (productVariantId != null) {
                predicates.add(cb.equal(root.get("productVariantId"), productVariantId));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productVariant").get("product").get("id"), productId));
            }
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("bomCode")), searchPattern),
                        cb.like(cb.lower(root.get("name")), searchPattern),
                        cb.like(cb.lower(root.get("productVariant").get("sku")), searchPattern),
                        cb.like(cb.lower(root.get("productVariant").get("variantName")), searchPattern),
                        cb.like(cb.lower(root.get("productVariant").get("product").get("name")), searchPattern)
                ));
            }
            if (version != null) predicates.add(cb.equal(root.get("bomVersion"), version));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = repository.findAll(spec, pageable);
        List<Bom> boms = page.getContent();

        List<UUID> bomIds = boms.stream().map(Bom::getId).toList();
        Map<UUID, List<BomItem>> itemsByBom = bomIds.isEmpty()
                ? Map.of()
                : repository.findItemsByBomIds(bomIds).stream()
                        .collect(Collectors.groupingBy(i -> i.getBom().getId()));

        return PageResponse.from(page.map(bom -> mapper.toResponse(bom, itemsByBom.getOrDefault(bom.getId(), List.of()))));
    }

    @Override
    public BomResponse findById(UUID id) {
        Bom bom = repository.findByIdWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("BOM", id));
        return mapper.toResponse(bom);
    }

    @Override
    @Transactional
    public BomResponse create(BomRequest request) {
        validate(request, null);
        Bom bom = new Bom();
        apply(bom, request);

        // Auto-increment version from 1
        Integer nextVersion = getNextVersion(bom.getProductVariantId());
        bom.setBomVersion(nextVersion);

        if (bom.getBomCode() == null || bom.getBomCode().isBlank()) {
            bom.setBomCode(generateBomCode());
        }
        return mapper.toResponse(repository.save(bom));
    }

    @Override
    @Transactional
    public BomResponse update(UUID id, BomRequest request) {
        Bom bom = get(id);
        
        // Keep original variant and version
        Integer originalVersion = bom.getBomVersion();
        UUID originalVariant = bom.getProductVariantId();

        validate(request, id);
        apply(bom, request);

        // Enforce read-only variant and version
        bom.setBomVersion(originalVersion);
        bom.setProductVariantId(originalVariant);

        if (bom.getBomCode() == null || bom.getBomCode().isBlank()) {
            bom.setBomCode(generateBomCode());
        }
        return mapper.toResponse(repository.save(bom));
    }

    private Integer getNextVersion(UUID variantId) {
        Integer maxVersion = repository.findMaxVersionByProductVariantId(variantId);
        return maxVersion == null ? 1 : maxVersion + 1;
    }

    @Transactional
    public String generateBomCode() {
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("BOM")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("BOM");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });
        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        businessSequenceRepository.saveAndFlush(seq);
        return String.format("BOM-%05d", nextVal);
    }

    @Override
    @Transactional
    public BomResponse updateStatus(UUID id, Status status) {
        Bom bom = get(id);
        bom.setStatus(status);
        return mapper.toResponse(repository.save(bom));
    }

    private void apply(Bom bom, BomRequest request) {
        if (!productVariantRepository.existsById(request.productVariantId())) {
            throw new ResourceNotFoundException("Product variant", request.productVariantId());
        }
        bom.setProductVariantId(request.productVariantId());
        bom.setName(request.name());
        bom.setBomCode(request.bomCode());
        bom.setDefault(request.isDefault());
        bom.setDescription(request.description());
        bom.setBomVersion(request.version());
        bom.setStatus(request.status());
        bom.setEffectiveFrom(request.effectiveFrom());
        bom.setEffectiveTo(request.effectiveTo());

        if (bom.isDefault()) {
            repository.clearDefaultBomsForVariant(bom.getProductVariantId(), bom.getId() == null ? UUID.randomUUID() : bom.getId());
        }

        bom.getItems().clear();
        for (BomRequest.ItemRequest itemRequest : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemRequest.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient", itemRequest.ingredientId()));
            if (Boolean.TRUE.equals(ingredient.getIsDeleted())) {
                throw new BadRequestException("Ingredient with id " + itemRequest.ingredientId() + " is deleted");
            }
            Unit unit = unitRepository.findById(itemRequest.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", itemRequest.unitId()));
            if (Boolean.TRUE.equals(unit.getIsDeleted())) {
                throw new BadRequestException("Unit with id " + itemRequest.unitId() + " is deleted");
            }
            BomItem item = new BomItem();
            item.setIngredient(ingredient);
            item.setQuantity(itemRequest.quantity());
            item.setUnit(unit);
            item.setWasteRate(itemRequest.wasteRate());
            bom.addItem(item);
        }
    }

    private void validate(BomRequest request, UUID id) {
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be after effectiveFrom");
        }
        if (request.bomCode() != null && !request.bomCode().isBlank()) {
            boolean codeExists = id == null
                    ? repository.existsByBomCode(request.bomCode())
                    : repository.existsByBomCodeAndIdNot(request.bomCode(), id);
            if (codeExists) throw new BadRequestException("BOM code already exists: " + request.bomCode());
        }
    }

    private Bom get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("BOM", id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Bom bom = get(id);
        if (bom.getIsDeleted()) {
            throw new ResourceNotFoundException("BOM", id);
        }
        bom.setIsDeleted(true);
        repository.save(bom);
    }

    @Override
    public List<BomLookupResponse> lookupByVariant(UUID productVariantId, Status status) {
        Status effectiveStatus = status != null ? status : Status.ACTIVE;
        return repository.findLookupByVariantAndStatus(productVariantId, effectiveStatus).stream()
                .map(mapper::toLookupResponse)
                .toList();
    }
}

