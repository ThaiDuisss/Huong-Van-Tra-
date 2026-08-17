package org.hvtra.iventoryservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.client.CatalogClient;
import org.hvtra.iventoryservice.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.hvtra.iventoryservice.entity.InventoryItem;
import org.hvtra.iventoryservice.shared.enums.InventoryItemType;
import org.hvtra.iventoryservice.shared.enums.InventoryStatus;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.service.InventoryStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@Service
@Transactional(readOnly = true)
public class InventoryStockServiceImpl implements InventoryStockService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryItemRepository itemRepository;
    private final CatalogClient catalogClient;

    public InventoryStockServiceImpl(
            InventoryBalanceRepository balanceRepository,
            InventoryItemRepository itemRepository,
            CatalogClient catalogClient) {
        this.balanceRepository = balanceRepository;
        this.itemRepository = itemRepository;
        this.catalogClient = catalogClient;
    }

    @Override
    public List<StockBatchResponse> getStocksByProductIds(StockBatchRequest request) {
        if (request == null || request.productIds() == null || request.productIds().isEmpty()) {
            throw new BadRequestException("productIds không được để trống");
        }

        List<UUID> productIds = request.productIds().stream()
                .filter(Objects::nonNull)
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

        Map<UUID, InventoryItem> itemByVariantId = itemRepository
                .findByReferenceIdInAndItemType(variantToProduct.keySet(), InventoryItemType.PRODUCT_VARIANT)
                .stream()
                .filter(item -> item.getStatus() == InventoryStatus.ACTIVE)
                .collect(Collectors.toMap(
                        InventoryItem::getReferenceId,
                        item -> item,
                        (left, ignored) -> left));
        if (itemByVariantId.isEmpty()) {
            return productIds.stream()
                    .map(productId -> new StockBatchResponse(productId, ZERO, ZERO, "OUT_OF_STOCK"))
                    .toList();
        }

        List<InventoryBalance> balances = balanceRepository.findActiveWarehouseBalancesByInventoryItemIds(
                itemByVariantId.values().stream().map(InventoryItem::getId).toList()
//                ,
//                request.warehouseId(),
//                InventoryLocationType.WAREHOUSE,
//                InventoryStatus.ACTIVE
        );

        Map<UUID, UUID> itemIdToProductId = itemByVariantId.values().stream()
                .collect(Collectors.toMap(
                        InventoryItem::getId,
                        item -> variantToProduct.get(item.getReferenceId()),
                        (left, ignored) -> left));

        Map<UUID, Totals> totalsByProductId = new HashMap<>();
        for (InventoryBalance balance : balances) {
            UUID productId = itemIdToProductId.get(balance.getInventoryItem().getId());
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

    @Override
    public List<StockVariantBatchResponse> getStocksByVariantIds(StockVariantBatchRequest request) {
        if (request == null || request.variantIds() == null || request.variantIds().isEmpty()) {
            throw new BadRequestException("variantIds không được để trống");
        }

        List<UUID> variantIds = request.variantIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (variantIds.isEmpty()) {
            throw new BadRequestException("variantIds không được để trống");
        }

        Map<UUID, InventoryItem> itemByVariantId = itemRepository
                .findByReferenceIdInAndItemType(variantIds, InventoryItemType.PRODUCT_VARIANT)
                .stream()
                .filter(item -> item.getStatus() == InventoryStatus.ACTIVE)
                .collect(Collectors.toMap(
                        InventoryItem::getReferenceId,
                        item -> item,
                        (left, ignored) -> left));
        if (itemByVariantId.isEmpty()) {
            return variantIds.stream()
                    .map(variantId -> new StockVariantBatchResponse(
                            variantId,
                            request.warehouseId(),
                            ZERO,
                            ZERO,
                            "OUT_OF_STOCK"))
                    .toList();
        }
        for (InventoryItem item : itemByVariantId.values()) {
            if (item.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("variantIds không tồn tại");
            }
        }




        List<InventoryBalance> balances = balanceRepository.findActiveWarehouseBalancesByInventoryItemIds(
                itemByVariantId.values().stream().map(InventoryItem::getId).toList()
//                request.warehouseId(),
//                InventoryLocationType.WAREHOUSE,
//                InventoryStatus.ACTIVE
        );


        Map<UUID, InventoryBalance> balanceByItemId = selectBestBalanceByItemId(balances);

        return variantIds.stream()
                .map(variantId -> {
                    InventoryItem item = itemByVariantId.get(variantId);
                    InventoryBalance balance = item == null ? null : balanceByItemId.get(item.getId());
                    return toVariantResponse(variantId, balance, request.warehouseId());
                })
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

    private StockVariantBatchResponse toVariantResponse(UUID variantId, InventoryBalance balance, UUID warehouseId) {
        if (balance == null) {
            return new StockVariantBatchResponse(variantId, warehouseId, ZERO, ZERO, "OUT_OF_STOCK");
        }
        BigDecimal reserved = balance.getQuantityReserved() != null ? balance.getQuantityReserved() : ZERO;
        BigDecimal available = (balance.getQuantityOnHand() != null ? balance.getQuantityOnHand() : ZERO)
                .subtract(reserved);
        if (available.compareTo(ZERO) < 0) {
            available = ZERO;
        }
        return new StockVariantBatchResponse(
                variantId,
                balance.getLocation().getId(),
                available,
                reserved,
                available.compareTo(ZERO) > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
    }

    private Map<UUID, InventoryBalance> selectBestBalanceByItemId(List<InventoryBalance> balances) {
        return balances.stream()
                .collect(Collectors.toMap(
                        balance -> balance.getInventoryItem().getId(),
                        balance -> balance,
                        this::pickBalanceWithMoreAvailable));
    }

    private InventoryBalance pickBalanceWithMoreAvailable(InventoryBalance left, InventoryBalance right) {
        BigDecimal leftAvailable = availableQuantity(left);
        BigDecimal rightAvailable = availableQuantity(right);
        return rightAvailable.compareTo(leftAvailable) > 0 ? right : left;
    }

    private BigDecimal availableQuantity(InventoryBalance balance) {
        if (balance == null) {
            return ZERO;
        }
        BigDecimal onHand = balance.getQuantityOnHand() != null ? balance.getQuantityOnHand() : ZERO;
        BigDecimal reserved = balance.getQuantityReserved() != null ? balance.getQuantityReserved() : ZERO;
        BigDecimal available = onHand.subtract(reserved);
        return available.compareTo(ZERO) < 0 ? ZERO : available;
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
