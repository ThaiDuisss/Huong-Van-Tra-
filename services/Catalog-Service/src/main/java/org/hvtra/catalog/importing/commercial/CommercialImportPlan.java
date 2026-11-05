package org.hvtra.catalog.importing.commercial;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reviewed JSON commands, not an entity projection or an ordinary create request. */
public record CommercialImportPlan(
        String sourceSystem, String contractVersion, String contractSha256,
        String sourceSnapshotSha256, List<String> commercialDecisionReferences,
        List<CategoryInput> categories, List<ProductInput> products, List<VariantInput> variants) {

    public CommercialImportPlan {
        commercialDecisionReferences = commercialDecisionReferences == null ? List.of() : List.copyOf(commercialDecisionReferences);
        categories = categories == null ? List.of() : List.copyOf(categories);
        products = products == null ? List.of() : List.copyOf(products);
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>(categories);
        rows.addAll(products);
        rows.addAll(variants);
        return List.copyOf(rows);
    }

    public enum Kind {
        CATEGORY("categories", "category_code", "name"),
        PRODUCT("products", "product_code", "name"),
        VARIANT("product_variants", "sku", "variant_name");

        final String table;
        final String codeColumn;
        final String nameColumn;

        Kind(String table, String codeColumn, String nameColumn) {
            this.table = table;
            this.codeColumn = codeColumn;
            this.nameColumn = nameColumn;
        }
    }

    /** CREATE_SEPARATE permits a reviewed name collision, never an identity/code collision. */
    public enum IdentityDecision { PENDING_REVIEW, CREATE, CREATE_SEPARATE }

    public sealed interface Row permits CategoryInput, ProductInput, VariantInput {
        UUID id();
        String code();
        String name();
        Status status();
        IdentityDecision decision();
        String approvalReference();
        Kind kind();
        Object businessRequest();
    }

    public record CategoryInput(
            @NotNull UUID id, UUID parentId,
            @NotBlank @Size(max = 50) String categoryCode,
            @NotBlank @Size(max = 255) String name,
            Status status, Integer defaultReorderPoint,
            IdentityDecision decision, String approvalReference) implements Row {
        public String code() { return categoryCode; }
        public Kind kind() { return Kind.CATEGORY; }
        public Object businessRequest() {
            return new CategoryRequest(parentId, categoryCode, name, status, defaultReorderPoint);
        }
    }

    public record ProductInput(
            @NotNull UUID id, UUID categoryId,
            @NotBlank @Size(max = 50) String productCode,
            @NotBlank @Size(max = 255) String name,
            String description, String imageUrl, Status status,
            IdentityDecision decision, String approvalReference) implements Row {
        public String code() { return productCode; }
        public Kind kind() { return Kind.PRODUCT; }
        public Object businessRequest() {
            return new ProductRequest(categoryId, productCode, name, description, imageUrl, status, null);
        }
    }

    public record VariantInput(
            @NotNull UUID id, UUID productId,
            @NotBlank @Size(max = 100) String sku,
            @NotBlank @Size(max = 255) String variantName,
            @Digits(integer = 16, fraction = 2) BigDecimal sellingPrice,
            UUID unitId, @Digits(integer = 16, fraction = 2) BigDecimal quantity,
            @Digits(integer = 16, fraction = 2) BigDecimal costPrice,
            InventoryPolicy inventoryPolicy, Status status,
            @Size(max = 100) String barcode,
            IdentityDecision decision, String approvalReference) implements Row {
        public String code() { return sku; }
        public String name() { return variantName; }
        public Kind kind() { return Kind.VARIANT; }
        public Object businessRequest() {
            return new ProductVariantRequest(quantity, sellingPrice, costPrice, unitId,
                    null, inventoryPolicy, status, barcode);
        }
    }
}
