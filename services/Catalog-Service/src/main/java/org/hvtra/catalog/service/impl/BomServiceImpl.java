package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.BomRequest;
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

@Service
@Transactional(readOnly = true)
public class BomServiceImpl implements BomService {
    private final BomRepository repository;
    private final ProductVariantRepository productVariantRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitRepository unitRepository;
    private final BomMapper mapper;

    public BomServiceImpl(
            BomRepository repository,
            ProductVariantRepository productVariantRepository,
            IngredientRepository ingredientRepository,
            UnitRepository unitRepository,
            BomMapper mapper) {
        this.repository = repository;
        this.productVariantRepository = productVariantRepository;
        this.ingredientRepository = ingredientRepository;
        this.unitRepository = unitRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<BomResponse> findAll(
            Long productVariantId, Integer version, Status status, Pageable pageable) {
        Specification<Bom> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (productVariantId != null) {
                predicates.add(cb.equal(root.get("productVariantId"), productVariantId));
            }
            if (version != null) predicates.add(cb.equal(root.get("version"), version));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public BomResponse findById(Long id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public BomResponse create(BomRequest request) {
        validate(request, null);
        Bom bom = new Bom();
        apply(bom, request);
        return mapper.toResponse(repository.save(bom));
    }

    @Override
    @Transactional
    public BomResponse update(Long id, BomRequest request) {
        Bom bom = get(id);
        validate(request, id);
        apply(bom, request);
        return mapper.toResponse(repository.save(bom));
    }

    @Override
    @Transactional
    public BomResponse updateStatus(Long id, Status status) {
        Bom bom = get(id);
        if (status == Status.ACTIVE
                && repository.existsByProductVariantIdAndStatusAndIdNot(
                        bom.getProductVariantId(), Status.ACTIVE, id)) {
            throw new BadRequestException("Another active BOM exists for this product variant");
        }
        bom.setStatus(status);
        return mapper.toResponse(repository.save(bom));
    }

    private void apply(Bom bom, BomRequest request) {
        if (!productVariantRepository.existsById(request.productVariantId())) {
            throw new ResourceNotFoundException("Product variant", request.productVariantId());
        }
        bom.setProductVariantId(request.productVariantId());
        bom.setVersion(request.version());
        bom.setStatus(request.status());
        bom.setEffectiveFrom(request.effectiveFrom());
        bom.setEffectiveTo(request.effectiveTo());
        bom.getItems().clear();
        for (BomRequest.ItemRequest itemRequest : request.items()) {
            Ingredient ingredient = ingredientRepository.findById(itemRequest.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient", itemRequest.ingredientId()));
            Unit unit = unitRepository.findById(itemRequest.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", itemRequest.unitId()));
            BomItem item = new BomItem();
            item.setIngredient(ingredient);
            item.setQuantity(itemRequest.quantity());
            item.setUnit(unit);
            item.setWasteRate(itemRequest.wasteRate());
            bom.addItem(item);
        }
    }

    private void validate(BomRequest request, Long id) {
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be after effectiveFrom");
        }
        boolean versionExists = id == null
                ? repository.existsByProductVariantIdAndVersion(request.productVariantId(), request.version())
                : repository.existsByProductVariantIdAndVersionAndIdNot(
                        request.productVariantId(), request.version(), id);
        if (versionExists) throw new BadRequestException("BOM version already exists for this product variant");
        if (request.status() == Status.ACTIVE
                && repository.existsByProductVariantIdAndStatusAndIdNot(
                        request.productVariantId(), Status.ACTIVE, id == null ? -1L : id)) {
            throw new BadRequestException("Another active BOM exists for this product variant");
        }
    }

    private Bom get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("BOM", id));
    }
}
