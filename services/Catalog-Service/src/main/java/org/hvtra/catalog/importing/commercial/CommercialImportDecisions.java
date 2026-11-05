package org.hvtra.catalog.importing.commercial;

import org.hvtra.catalog.enums.InventoryPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.CategoryInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.VariantInput;

/** Explicit group membership and reviewed values. No rule is approved by default. */
public record CommercialImportDecisions(List<CategoryRule> categoryRules, List<VariantRule> variantRules) {
    public record CategoryRule(String ruleId, boolean approved, String approvalReference,
                               List<UUID> categoryIds, Integer defaultReorderPoint) { }
    public record VariantRule(String ruleId, boolean approved, String approvalReference,
                              List<UUID> variantIds, UUID unitId, BigDecimal quantity,
                              BigDecimal costPrice, InventoryPolicy inventoryPolicy) { }

    public CommercialImportDecisions {
        categoryRules = categoryRules == null ? List.of() : List.copyOf(categoryRules);
        variantRules = variantRules == null ? List.of() : List.copyOf(variantRules);
    }

    CommercialImportPlan apply(CommercialImportPlan plan) {
        Objects.requireNonNull(plan, "IMPORT_PLAN_REQUIRED");
        Map<UUID, CategoryInput> categories = new LinkedHashMap<>();
        Map<UUID, VariantInput> variants = new LinkedHashMap<>();
        plan.categories().forEach(row -> { if (categories.put(row.id(), row) != null) throw new IllegalArgumentException("DUPLICATE_CATEGORY_ID"); });
        plan.variants().forEach(row -> { if (variants.put(row.id(), row) != null) throw new IllegalArgumentException("DUPLICATE_VARIANT_ID"); });
        LinkedHashSet<String> refs = new LinkedHashSet<>(plan.commercialDecisionReferences());
        for (CategoryRule rule : categoryRules) {
            if (!rule.approved()) continue;
            approved(rule.ruleId(), rule.approved(), rule.approvalReference(), rule.categoryIds());
            if (rule.defaultReorderPoint() == null || rule.defaultReorderPoint() < 0) throw new IllegalArgumentException("CATEGORY_REORDER_VALUE_REQUIRED");
            for (UUID id : rule.categoryIds()) {
                CategoryInput old = categories.get(id);
                if (old == null) throw new IllegalArgumentException("UNKNOWN_CATEGORY_RULE_MEMBER");
                Integer value = fill(old.defaultReorderPoint(), rule.defaultReorderPoint());
                categories.put(id, new CategoryInput(old.id(), old.parentId(), old.categoryCode(), old.name(), old.status(), value, old.decision(), old.approvalReference()));
            }
            refs.add(rule.approvalReference());
        }
        for (VariantRule rule : variantRules) {
            if (!rule.approved()) continue;
            approved(rule.ruleId(), rule.approved(), rule.approvalReference(), rule.variantIds());
            if (rule.unitId() == null && rule.quantity() == null && rule.costPrice() == null && rule.inventoryPolicy() == null) throw new IllegalArgumentException("COMMERCIAL_RULE_HAS_NO_VALUES");
            for (UUID id : rule.variantIds()) {
                VariantInput old = variants.get(id);
                if (old == null) throw new IllegalArgumentException("UNKNOWN_VARIANT_RULE_MEMBER");
                variants.put(id, new VariantInput(old.id(), old.productId(), old.sku(), old.variantName(), old.sellingPrice(),
                        fill(old.unitId(), rule.unitId()), fill(old.quantity(), rule.quantity()), fill(old.costPrice(), rule.costPrice()),
                        fill(old.inventoryPolicy(), rule.inventoryPolicy()), old.status(), old.barcode(), old.decision(), old.approvalReference()));
            }
            refs.add(rule.approvalReference());
        }
        // Rule approval never fills mapping approvalReference or changes a pending identity decision.
        return new CommercialImportPlan(plan.sourceSystem(), plan.contractVersion(), plan.contractSha256(), plan.sourceSnapshotSha256(),
                new ArrayList<>(refs), new ArrayList<>(categories.values()), plan.products(), new ArrayList<>(variants.values()));
    }

    private static void approved(String id, boolean approved, String reference, List<UUID> members) {
        if (id == null || id.isBlank() || !approved || reference == null || reference.isBlank() || reference.length() > 500
                || members == null || members.isEmpty() || members.contains(null) || new LinkedHashSet<>(members).size() != members.size()) throw new IllegalArgumentException("EXPLICIT_APPROVED_RULE_AND_EXACT_MEMBERSHIP_REQUIRED");
    }

    private static <T> T fill(T existing, T proposed) {
        if (proposed == null) return existing;
        boolean equal = existing instanceof BigDecimal a && proposed instanceof BigDecimal b ? a.compareTo(b) == 0 : Objects.equals(existing, proposed);
        if (existing != null && !equal) throw new IllegalArgumentException("CONFLICTING_RULE_OR_SOURCE_VALUE_OVERWRITE");
        return existing == null ? proposed : existing;
    }
}
