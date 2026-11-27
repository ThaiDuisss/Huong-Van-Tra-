package org.hvtra.catalog.service.bundle;

import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.ComponentPayload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chuẩn hóa danh sách nguyên liệu, đơn vị và khối lượng về dạng canonical rồi băm SHA-256.
 * Cùng nguyên liệu và cùng khối lượng thực tế (dù khác thứ tự hay g/kg) luôn cho cùng recipe_sha256.
 */
@Component
public class RecipeCanonicalHasher {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    public CanonicalRecipe canonicalize(
            List<ComponentPayload> components,
            Map<UUID, Ingredient> ingredientsById,
            List<Unit> activeUnits) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Bundle components must not be empty");
        }
        Unit gramUnit = findGramUnit(activeUnits);
        Map<String, Unit> unitBySymbol = indexUnitsBySymbol(activeUnits);
        Map<UUID, NormalizedBucket> merged = new LinkedHashMap<>();

        for (ComponentPayload component : components) {
            if (component == null || component.getIngredientId() == null || component.getQuantity() == null
                    || component.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException("Invalid bundle component quantity or ingredientId");
            }
            Ingredient ingredient = ingredientsById.get(component.getIngredientId());
            if (ingredient == null || Boolean.TRUE.equals(ingredient.getIsDeleted())) {
                throw new IllegalArgumentException("Ingredient not found for bundle: " + component.getIngredientId());
            }

            Unit sourceUnit = resolveComponentUnit(component.getUnit(), ingredient, unitBySymbol);
            NormalizedMeasurement measurement = normalizeMeasurement(component.getQuantity(), sourceUnit, gramUnit);

            NormalizedBucket existing = merged.get(ingredient.getId());
            if (existing == null) {
                merged.put(ingredient.getId(), new NormalizedBucket(
                        ingredient,
                        measurement.unitGroupKey(),
                        measurement.storageUnit(),
                        measurement.canonicalQuantity(),
                        measurement.storageQuantity()));
            } else {
                if (!existing.unitGroupKey().equals(measurement.unitGroupKey())) {
                    throw new IllegalArgumentException("Incompatible units for ingredient " + ingredient.getId());
                }
                merged.put(ingredient.getId(), new NormalizedBucket(
                        ingredient,
                        existing.unitGroupKey(),
                        existing.storageUnit(),
                        existing.canonicalQuantity().add(measurement.canonicalQuantity()),
                        existing.storageQuantity().add(measurement.storageQuantity())));
            }
        }

        List<NormalizedIngredientLine> sortedLines = merged.values().stream()
                .map(bucket -> new NormalizedIngredientLine(
                        bucket.ingredient(),
                        bucket.unitGroupKey(),
                        bucket.storageUnit(),
                        bucket.canonicalQuantity().stripTrailingZeros(),
                        bucket.storageQuantity().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(line -> line.ingredient().getId().toString()))
                .toList();

        for (NormalizedIngredientLine line : sortedLines) {
            if (line.storageQuantity().compareTo(new BigDecimal("0.01")) < 0) {
                throw new IllegalArgumentException(
                        "Normalized BOM quantity below minimum 0.01 for ingredient " + line.ingredient().getId());
            }
        }

        String canonicalPayload = sortedLines.stream()
                .map(line -> line.ingredient().getId()
                        + ":" + line.unitGroupKey()
                        + ":" + plainDecimal(line.canonicalQuantity()))
                .reduce("v1", (acc, part) -> acc + "|" + part);

        return new CanonicalRecipe(sha256Hex(canonicalPayload), sortedLines);
    }

    private Unit resolveComponentUnit(String unitText, Ingredient ingredient, Map<String, Unit> unitBySymbol) {
        if (unitText != null && !unitText.isBlank()) {
            Unit matched = unitBySymbol.get(unitText.trim().toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        if (ingredient.getUnit() != null) {
            return ingredient.getUnit();
        }
        throw new IllegalArgumentException("Cannot resolve unit for ingredient " + ingredient.getId());
    }

    private NormalizedMeasurement normalizeMeasurement(BigDecimal rawQuantity, Unit sourceUnit, Unit gramUnit) {
        Unit current = sourceUnit;
        BigDecimal factor = BigDecimal.ONE;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            BigDecimal rate = current.getConversionRate() != null && current.getConversionRate().signum() > 0
                    ? current.getConversionRate()
                    : BigDecimal.ONE;
            factor = factor.multiply(rate);
            if (current.getBaseUnit() == null) {
                break;
            }
            current = current.getBaseUnit();
        }

        Unit rootUnit = current != null ? current : sourceUnit;
        if (rootUnit.getType() == UnitType.WEIGHT || isWeightSymbol(sourceUnit.getSymbol())) {
            // In catalog_db, Gram has conversionRate=1 and Kilogram has conversionRate=1000 (both baseUnit=null).
            BigDecimal gramFactor = resolveWeightGramFactor(sourceUnit, factor);
            BigDecimal grams = rawQuantity.multiply(gramFactor);
            Unit storageUnit = gramUnit != null ? gramUnit : sourceUnit;
            return new NormalizedMeasurement("WEIGHT:G", grams, storageUnit, grams);
        }

        BigDecimal baseQuantity = rawQuantity.multiply(factor);
        return new NormalizedMeasurement("UNIT:" + rootUnit.getId(), baseQuantity, rootUnit, baseQuantity);
    }

    private BigDecimal resolveWeightGramFactor(Unit sourceUnit, BigDecimal chainedFactor) {
        if (sourceUnit.getBaseUnit() != null) {
            return chainedFactor;
        }
        String symbol = sourceUnit.getSymbol() == null ? "" : sourceUnit.getSymbol().trim().toLowerCase(Locale.ROOT);
        if ("kg".equals(symbol)) {
            return sourceUnit.getConversionRate() != null && sourceUnit.getConversionRate().signum() > 0
                    ? sourceUnit.getConversionRate()
                    : THOUSAND;
        }
        if ("g".equals(symbol)) {
            return BigDecimal.ONE;
        }
        return chainedFactor;
    }

    private static boolean isWeightSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        String normalized = symbol.trim().toLowerCase(Locale.ROOT);
        return "g".equals(normalized) || "kg".equals(normalized);
    }

    private static Unit findGramUnit(List<Unit> activeUnits) {
        return activeUnits.stream()
                .filter(u -> u.getSymbol() != null && "g".equalsIgnoreCase(u.getSymbol().trim()))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, Unit> indexUnitsBySymbol(List<Unit> activeUnits) {
        Map<String, Unit> bySymbol = new HashMap<>();
        for (Unit unit : activeUnits) {
            if (Boolean.TRUE.equals(unit.getIsDeleted())) {
                continue;
            }
            if (unit.getSymbol() != null && !unit.getSymbol().isBlank()) {
                bySymbol.putIfAbsent(unit.getSymbol().trim().toLowerCase(Locale.ROOT), unit);
            }
            if (unit.getName() != null && !unit.getName().isBlank()) {
                bySymbol.putIfAbsent(unit.getName().trim().toLowerCase(Locale.ROOT), unit);
            }
        }
        return bySymbol;
    }

    private static String plainDecimal(BigDecimal value) {
        BigDecimal normalized = value.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return normalized.toPlainString();
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record CanonicalRecipe(String recipeSha256, List<NormalizedIngredientLine> lines) {
    }

    public record NormalizedIngredientLine(
            Ingredient ingredient,
            String unitGroupKey,
            Unit storageUnit,
            BigDecimal canonicalQuantity,
            BigDecimal storageQuantity) {
    }

    private record NormalizedMeasurement(
            String unitGroupKey,
            BigDecimal canonicalQuantity,
            Unit storageUnit,
            BigDecimal storageQuantity) {
    }

    private record NormalizedBucket(
            Ingredient ingredient,
            String unitGroupKey,
            Unit storageUnit,
            BigDecimal canonicalQuantity,
            BigDecimal storageQuantity) {
    }
}
