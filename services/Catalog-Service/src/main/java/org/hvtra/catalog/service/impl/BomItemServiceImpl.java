package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.BomItemRequest;
import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.entity.BomItem;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.BomItemMapper;
import org.hvtra.catalog.repository.BomItemRepository;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.BomItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BomItemServiceImpl implements BomItemService {
    private final BomItemRepository repository;
    private final ProductVariantRepository productVariantRepository;
    private final IngredientRepository ingredientRepository;
    private final UnitRepository unitRepository;
    private final BomItemMapper mapper;

    public BomItemServiceImpl(
            BomItemRepository repository,
            ProductVariantRepository productVariantRepository,
            IngredientRepository ingredientRepository,
            UnitRepository unitRepository,
            BomItemMapper mapper) {
        this.repository = repository;
        this.productVariantRepository = productVariantRepository;
        this.ingredientRepository = ingredientRepository;
        this.unitRepository = unitRepository;
        this.mapper = mapper;
    }

    @Override
    public List<BomItemResponse> findByVariant(UUID productVariantId) {
        return repository.findByProductVariantId(productVariantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<BomItemResponse> replaceForVariant(UUID productVariantId, List<BomItemRequest> items) {
        if (!productVariantRepository.existsById(productVariantId)) {
            throw new ResourceNotFoundException("Product variant", productVariantId);
        }

        repository.deleteByProductVariantId(productVariantId);

        List<UUID> ingredientIds = items.stream().map(BomItemRequest::ingredientId).distinct().toList();
        List<UUID> unitIds = items.stream().map(BomItemRequest::unitId).distinct().toList();

        Map<UUID, Ingredient> ingredientById = ingredientRepository.findAllById(ingredientIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        Map<UUID, Unit> unitById = unitRepository.findAllById(unitIds).stream()
                .collect(Collectors.toMap(Unit::getId, Function.identity()));

        List<BomItem> toSave = items.stream().map(itemRequest -> {
            Ingredient ingredient = ingredientById.get(itemRequest.ingredientId());
            if (ingredient == null) {
                throw new ResourceNotFoundException("Ingredient", itemRequest.ingredientId());
            }
            if (Boolean.TRUE.equals(ingredient.getIsDeleted())) {
                throw new BadRequestException("Ingredient with id " + itemRequest.ingredientId() + " is deleted");
            }
            Unit unit = unitById.get(itemRequest.unitId());
            if (unit == null) {
                throw new ResourceNotFoundException("Unit", itemRequest.unitId());
            }
            if (Boolean.TRUE.equals(unit.getIsDeleted())) {
                throw new BadRequestException("Unit with id " + itemRequest.unitId() + " is deleted");
            }

            BomItem item = new BomItem();
            item.setProductVariantId(productVariantId);
            item.setIngredient(ingredient);
            item.setQuantity(itemRequest.quantity());
            item.setUnit(unit);
            item.setWasteRate(itemRequest.wasteRate());
            return item;
        }).toList();

        List<BomItem> saved = repository.saveAll(toSave);
        return saved.stream().map(mapper::toResponse).toList();
    }
}
