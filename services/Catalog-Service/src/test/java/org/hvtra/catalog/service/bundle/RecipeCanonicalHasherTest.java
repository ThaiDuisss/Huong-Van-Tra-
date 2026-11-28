package org.hvtra.catalog.service.bundle;

import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.ComponentPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RecipeCanonicalHasherTest {

    private final RecipeCanonicalHasher hasher = new RecipeCanonicalHasher();

    @Test
    void sameIngredientsAndWeightsProduceIdenticalHashRegardlessOfOrderAndWeightUnits() {
        Unit gram = unit(UUID.fromString("e1000000-0000-0000-0000-000000000004"), "Gram", "g", UnitType.WEIGHT, "1.0000");
        Unit kilogram = unit(UUID.fromString("e1000000-0000-0000-0000-000000000003"), "Kilogram", "kg", UnitType.WEIGHT, "1000.0000");
        UUID ingAId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
        UUID ingBId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
        Ingredient ingA = ingredient(ingAId, "ING-A", gram);
        Ingredient ingB = ingredient(ingBId, "ING-B", kilogram);

        var first = hasher.canonicalize(
                List.of(
                        component(ingAId, "ING-A", "25", "g"),
                        component(ingBId, "ING-B", "0.050", "kg")),
                Map.of(ingAId, ingA, ingBId, ingB),
                List.of(gram, kilogram));

        var second = hasher.canonicalize(
                List.of(
                        component(ingBId, "ING-B", "50.00", "g"),
                        component(ingAId, "ING-A", "0.025", "kg")),
                Map.of(ingAId, ingA, ingBId, ingB),
                List.of(gram, kilogram));

        assertEquals(first.recipeSha256(), second.recipeSha256());
        assertEquals(2, first.lines().size());
        assertEquals(new BigDecimal("25.00"), first.lines().get(0).storageQuantity());
        assertEquals("g", first.lines().get(0).storageUnit().getSymbol());
        assertEquals(new BigDecimal("50.00"), first.lines().get(1).storageQuantity());
        assertEquals("g", first.lines().get(1).storageUnit().getSymbol());
    }

    @Test
    void sameRatioDifferentTotalWeightProducesDifferentHash() {
        Unit gram = unit(UUID.fromString("e1000000-0000-0000-0000-000000000004"), "Gram", "g", UnitType.WEIGHT, "1.0000");
        UUID ingAId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
        UUID ingBId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
        Ingredient ingA = ingredient(ingAId, "ING-A", gram);
        Ingredient ingB = ingredient(ingBId, "ING-B", gram);

        var small = hasher.canonicalize(
                List.of(component(ingAId, "ING-A", "10", "g"), component(ingBId, "ING-B", "20", "g")),
                Map.of(ingAId, ingA, ingBId, ingB),
                List.of(gram));
        var doubleWeight = hasher.canonicalize(
                List.of(component(ingAId, "ING-A", "20", "g"), component(ingBId, "ING-B", "40", "g")),
                Map.of(ingAId, ingA, ingBId, ingB),
                List.of(gram));

        assertNotEquals(small.recipeSha256(), doubleWeight.recipeSha256());
    }

    private static Unit unit(UUID id, String name, String symbol, UnitType type, String conversionRate) {
        Unit u = new Unit();
        u.setId(id);
        u.setName(name);
        u.setSymbol(symbol);
        u.setType(type);
        u.setPrecisionScale(0);
        u.setConversionRate(new BigDecimal(conversionRate));
        u.setStatus(Status.ACTIVE);
        u.setIsDeleted(false);
        return u;
    }

    private static Ingredient ingredient(UUID id, String code, Unit unit) {
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setIngredientCode(code);
        i.setName(code);
        i.setUnit(unit);
        i.setType(1);
        i.setStatus(Status.ACTIVE);
        i.setIsDeleted(false);
        return i;
    }

    private static ComponentPayload component(UUID ingredientId, String code, String quantity, String unit) {
        ComponentPayload c = new ComponentPayload();
        c.setIngredientId(ingredientId);
        c.setIngredientCode(code);
        c.setQuantity(new BigDecimal(quantity));
        c.setUnit(unit);
        return c;
    }
}
