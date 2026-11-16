package org.hvtra.iventoryservice.integration.catalog.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resilience4j decorator around the Catalog-Service Feign proxy. Being {@code @Primary}, it is the
 * bean every {@code CatalogClient} injection point receives, so no call can bypass it; a new method
 * on the interface does not compile until it is delegated here.
 *
 * <p>Class-level {@code @CircuitBreaker} + {@code @Bulkhead} cover every method. {@code @Retry} is
 * added per method and only on idempotent reads (GET, and the POST ".../batch" style lookups that
 * are pure queries on the Catalog side). Effective nesting: Retry( CircuitBreaker( Bulkhead( call ) ) ).
 *
 * <p>No fallbacks: callers that only need display data already catch and degrade; everything else
 * must fail. An open breaker surfaces as {@code CallNotPermittedException} and a full bulkhead as
 * {@code BulkheadFullException}, both answered with HTTP 503 by {@code GlobalException}.
 *
 * <p>Each method only delegates, so there is no self-invocation that would skip the AOP proxy.
 */
@Primary
@Component
@CircuitBreaker(name = ResilientCatalogClient.INSTANCE)
@Bulkhead(name = ResilientCatalogClient.INSTANCE)
public class ResilientCatalogClient implements CatalogClient {

    public static final String INSTANCE = "catalogService";

    private final CatalogClient delegate;

    public ResilientCatalogClient(@Qualifier(FEIGN_QUALIFIER) CatalogClient delegate) {
        this.delegate = delegate;
    }

    @Retry(name = INSTANCE)
    @Override
    public ProductResponse getProduct(UUID id) {
        return delegate.getProduct(id);
    }

    @Retry(name = INSTANCE)
    @Override
    public VariantResponse getVariant(UUID id) {
        return delegate.getVariant(id);
    }

    @Retry(name = INSTANCE)
    @Override
    public CategoryResponse getCategory(UUID id) {
        return delegate.getCategory(id);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<ProductResponse> getProductsBatch(List<UUID> ids) {
        return delegate.getProductsBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<VariantResponse> getVariantsBatch(List<UUID> ids) {
        return delegate.getVariantsBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<VariantCostResponse> getVariantCostsBatch(List<UUID> ids) {
        return delegate.getVariantCostsBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<VariantInventorySummaryResponse> getVariantInventorySummariesBatch(List<UUID> ids) {
        return delegate.getVariantInventorySummariesBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<ProductVariantProductRefResponse> getVariantRefsByProductIds(List<UUID> productIds) {
        return delegate.getVariantRefsByProductIds(productIds);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<CategoryResponse> getCategoriesBatch(List<UUID> ids) {
        return delegate.getCategoriesBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UnitResponse> getUnitsBatch(List<UUID> ids) {
        return delegate.getUnitsBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UUID> searchVariantIds(
            String keyword,
            String sku,
            String productCode,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId
    ) {
        return delegate.searchVariantIds(keyword, sku, productCode, productName, categoryCode, categoryId, unitId);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UUID> getSupplierIngredientIds(UUID supplierId) {
        return delegate.getSupplierIngredientIds(supplierId);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<IngredientResponse> getIngredientsBatch(List<UUID> ids) {
        return delegate.getIngredientsBatch(ids);
    }

    // WRITE, not idempotent: Catalog has no idempotency key and generates a new code when
    // none is given, so a retry after a lost response could create a duplicate. No @Retry.
    @Override
    public IngredientResponse createIngredient(String code, CreateIngredientRequest request) {
        return delegate.createIngredient(code, request);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UUID> searchIngredientIds(String keyword, String code, String name, UUID unitId) {
        return delegate.searchIngredientIds(keyword, code, name, unitId);
    }

    @Retry(name = INSTANCE)
    @Override
    public SupplierResponse getSupplier(UUID id) {
        return delegate.getSupplier(id);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<SupplierResponse> getSuppliersBatch(List<UUID> ids) {
        return delegate.getSuppliersBatch(ids);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<SupplierLookupResponse> getAllSuppliers() {
        return delegate.getAllSuppliers();
    }

    // WRITE: a set union on the Catalog side, but it carries no idempotency key and runs inside the
    // import-approval transaction right after createIngredient. Kept single-attempt. No @Retry.
    @Override
    public void addIngredientsToSupplier(UUID supplierId, List<UUID> ingredientIds) {
        delegate.addIngredientsToSupplier(supplierId, ingredientIds);
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UnitResponse> getAllUnits() {
        return delegate.getAllUnits();
    }

    @Retry(name = INSTANCE)
    @Override
    public List<BomItemResponse> getBomItems(UUID variantId) {
        return delegate.getBomItems(variantId);
    }
}
