package org.hvtra.catalog.client.gateway;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.exception.InventoryServiceUnavailableException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The only entry point Catalog code may use to call Inventory-Service over Feign. Do not inject
 * {@link InventoryClient} anywhere else: a direct call bypasses the circuit breaker and the bulkhead.
 *
 * <p>Aspect order (Resilience4j defaults): Retry( CircuitBreaker( Bulkhead( call ) ) ), so every retry
 * attempt is recorded by the breaker and an open breaker ends the retry loop at once.
 *
 * <p>Both endpoints are read-only batch look-ups (POST is used only to carry the id list; Inventory's
 * InventoryStockServiceImpl is {@code @Transactional(readOnly = true)}), so they are safe to retry.
 *
 * <p>Failure contract: an open breaker or a full bulkhead becomes
 * {@link InventoryServiceUnavailableException} (HTTP 503 through GlobalException). Every other error
 * (FeignClientException with the downstream status, RetryableException on timeout) is rethrown unchanged
 * so the existing error translation still applies. No value is ever fabricated here; a caller that only
 * decorates a response with stock may catch the exception and show "unknown stock" itself.
 *
 * <p>Callers must come from another bean: a call through {@code this} skips the Spring AOP proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryGateway {

    static final String INVENTORY_SERVICE = "inventoryService";

    private final InventoryClient inventoryClient;

    @Retry(name = INVENTORY_SERVICE)
    @CircuitBreaker(name = INVENTORY_SERVICE, fallbackMethod = "stocksByProductIdsRejected")
    @Bulkhead(name = INVENTORY_SERVICE)
    public List<InventoryClient.StockBatchResponse> getStocksByProductIds(
            InventoryClient.StockBatchRequest request
    ) {
        return inventoryClient.getStocksByProductIds(request);
    }

    @Retry(name = INVENTORY_SERVICE)
    @CircuitBreaker(name = INVENTORY_SERVICE, fallbackMethod = "stocksByVariantIdsRejected")
    @Bulkhead(name = INVENTORY_SERVICE)
    public List<InventoryClient.StockVariantBatchResponse> getStocksByVariantIds(
            InventoryClient.StockVariantBatchRequest request
    ) {
        return inventoryClient.getStocksByVariantIds(request);
    }

    private List<InventoryClient.StockBatchResponse> stocksByProductIdsRejected(
            InventoryClient.StockBatchRequest request,
            CallNotPermittedException exception
    ) {
        throw circuitOpen(exception);
    }

    private List<InventoryClient.StockBatchResponse> stocksByProductIdsRejected(
            InventoryClient.StockBatchRequest request,
            BulkheadFullException exception
    ) {
        throw bulkheadFull(exception);
    }

    private List<InventoryClient.StockVariantBatchResponse> stocksByVariantIdsRejected(
            InventoryClient.StockVariantBatchRequest request,
            CallNotPermittedException exception
    ) {
        throw circuitOpen(exception);
    }

    private List<InventoryClient.StockVariantBatchResponse> stocksByVariantIdsRejected(
            InventoryClient.StockVariantBatchRequest request,
            BulkheadFullException exception
    ) {
        throw bulkheadFull(exception);
    }

    private InventoryServiceUnavailableException circuitOpen(CallNotPermittedException exception) {
        log.warn("Inventory circuit breaker is OPEN, stock look-up was not sent");
        return new InventoryServiceUnavailableException(
                "Inventory Service đang tạm ngắt do lỗi liên tục. Vui lòng thử lại sau.",
                exception
        );
    }

    private InventoryServiceUnavailableException bulkheadFull(BulkheadFullException exception) {
        log.warn("Inventory bulkhead is full, stock look-up was not sent");
        return new InventoryServiceUnavailableException(
                "Hệ thống đang có quá nhiều yêu cầu tra cứu tồn kho. Vui lòng thử lại sau.",
                exception
        );
    }
}
