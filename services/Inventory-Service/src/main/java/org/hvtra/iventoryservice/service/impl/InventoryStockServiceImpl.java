package org.hvtra.iventoryservice.service.impl;

import org.hvtra.iventoryservice.client.CatalogClient;
import org.hvtra.iventoryservice.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.service.InventoryStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryStockServiceImpl implements InventoryStockService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InventoryBalanceRepository balanceRepository;
    private final CatalogClient catalogClient;

    public InventoryStockServiceImpl(
            InventoryBalanceRepository balanceRepository,
            CatalogClient catalogClient) {
        this.balanceRepository = balanceRepository;
        this.catalogClient = catalogClient;
    }

    @Override
    public List<StockBatchResponse> getStocksByProductIds(StockBatchRequest request) {
        if (request == null || request.productIds() == null || request.productIds().isEmpty()) {
            throw new BadRequestException("productIds không được để trống");
        }

        List<UUID> productIds = request.productIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            throw new BadRequestException("productIds không được để trống");
        }

        List<CatalogClient.ProductVariantProductRefResponse> variantRefs =
                catalogClient.getVariantRefsByProductIds(productIds);
        if (variantRefs == null || variantRefs.isEmpty()) {
            return productIds.stream()
                    .map(productId -> new StockBatchResponse(productId, ZERO, ZERO, "OUT_OF_STOCK"))
                    .toList();
        }

        Map<UUID, UUID> variantToProduct = variantRefs.stream()
                .filter(ref -> ref.id() != null && ref.productId() != null)
                .collect(Collectors.toMap(
                        CatalogClient.ProductVariantProductRefResponse::id,
                        CatalogClient.ProductVariantProductRefResponse::productId,
                        (left, ignored) -> left));
        if (variantToProduct.isEmpty()) {
            return productIds.stream()
                    .map(productId -> new StockBatchResponse(productId, ZERO, ZERO, "OUT_OF_STOCK"))
                    .toList();
        }

        List<InventoryBalance> balances = balanceRepository.findProductVariantBalances(
                variantToProduct.keySet(),
                InventoryItemType.PRODUCT_VARIANT,
                request.warehouseId());

        Map<UUID, Totals> totalsByProductId = new HashMap<>();
        for (InventoryBalance balance : balances) {
            UUID variantId = balance.getInventoryItem().getReferenceId();
            UUID productId = variantToProduct.get(variantId);
            if (productId == null) {
                continue;
            }
            totalsByProductId.computeIfAbsent(productId, ignored -> new Totals())
                    .add(balance.getQuantityOnHand(), balance.getQuantityReserved());
        }

        return productIds.stream()
                .map(productId -> toResponse(productId, totalsByProductId.get(productId)))
                .toList();
    }

    private StockBatchResponse toResponse(UUID productId, Totals totals) {
        if (totals == null) {
            return new StockBatchResponse(productId, ZERO, ZERO, "OUT_OF_STOCK");
        }
        BigDecimal available = totals.onHand.subtract(totals.reserved);
        if (available.compareTo(ZERO) < 0) {
            available = ZERO;
        }
        return new StockBatchResponse(
                productId,
                available,
                totals.reserved,
                available.compareTo(ZERO) > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
    }

    private static class Totals {
        private BigDecimal onHand = ZERO;
        private BigDecimal reserved = ZERO;

        private Totals add(BigDecimal quantityOnHand, BigDecimal quantityReserved) {
            onHand = onHand.add(quantityOnHand != null ? quantityOnHand : ZERO);
            reserved = reserved.add(quantityReserved != null ? quantityReserved : ZERO);
            return this;
        }
    }
}
