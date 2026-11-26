package org.hvtra.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.taste.TasteComposition;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/bom-recipes")
@RequiredArgsConstructor
public class InternalBomRecipeController {

    private static final int MAX_VARIANTS = 64;

    private final ProductVariantRepository variants;
    private final IngredientRepository ingredients;
    private final UnitRepository units;
    private final TasteComposition composition;

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RecipeBatch read(@RequestParam List<UUID> variantIds) {
        var caller = SecurityUtils.currentUser();
        if (!"service:recommendation-service".equals(caller.userId()) || !Set.of("SYSTEM").equals(caller.roles())) {
            throw new AccessDeniedException("BOM recipe read denied");
        }
        if (variantIds.isEmpty() || variantIds.size() > MAX_VARIANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid BOM recipe batch size");
        }
        List<ProductVariant> active = variants.findActiveBomRecipeVariants(variantIds, Status.ACTIVE);
        Map<UUID, TasteComposition.Basis> bases = composition.inspectAll(active);
        Set<UUID> ingredientIds = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        List<Recipe> recipes = new ArrayList<>();
        for (ProductVariant variant : active) {
            TasteComposition.Basis basis = bases.get(variant.getId());
            if (!basis.valid() || variant.getCompositionRevision() <= 0
                    || !basis.digest().equals(variant.getCompositionSha256())) {
                continue;
            }
            collectUnits(basis.document().path("unit"), unitIds);
            for (JsonNode line : basis.document().path("ingredients")) {
                ingredientIds.add(UUID.fromString(line.path("ingredientId").asText()));
                collectUnits(line.path("unit"), unitIds);
                collectUnits(line.path("ingredientUnit"), unitIds);
            }
            recipes.add(new Recipe(variant.getId(), variant.getProduct().getId(), variant.getSku(),
                    variant.getVariantName(), variant.getCompositionRevision(), basis.digest(), basis.document()));
        }
        Map<UUID, IngredientInfo> metadata = new HashMap<>();
        for (Ingredient ingredient : ingredients.findAllById(ingredientIds)) {
            metadata.put(ingredient.getId(), new IngredientInfo(ingredient.getIngredientCode(),
                    ingredient.getName(), ingredient.getRetailPricePerUnit()));
        }
        Map<UUID, UnitInfo> unitMetadata = new HashMap<>();
        for (Unit unit : units.findAllById(unitIds)) {
            unitMetadata.put(unit.getId(), new UnitInfo(unit.getSymbol(), unit.getPrecisionScale()));
        }
        return new RecipeBatch(recipes, metadata, unitMetadata);
    }

    private static void collectUnits(JsonNode chain, Collection<UUID> units) {
        for (JsonNode unit : chain) {
            units.add(UUID.fromString(unit.path("unitId").asText()));
        }
    }

    public record RecipeBatch(List<Recipe> recipes, Map<UUID, IngredientInfo> ingredients, Map<UUID, UnitInfo> units) { }

    public record Recipe(UUID variantId, UUID productId, String sku, String name, long compositionRevision,
                         String compositionSha256, JsonNode basis) { }

    public record IngredientInfo(String code, String name, BigDecimal retailPricePerUnit) { }

    public record UnitInfo(String symbol, int precisionScale) { }
}
