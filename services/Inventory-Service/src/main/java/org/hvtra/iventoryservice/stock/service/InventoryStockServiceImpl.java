package org.hvtra.iventoryservice.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.stock.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.stock.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.stock.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

        List<InventoryBalance> balances = balanceRepository.findSaleableBalancesByInventoryItemIds(
                itemByVariantId.values().stream().map(InventoryItem::getId).toList(),
                InventoryStatus.ACTIVE,
                InventoryLocationSaleability.nonSaleableTypes()
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
            if (InventoryLocationSaleability.isSaleable(balance.getLocation())) {
                totalsByProductId.computeIfAbsent(productId, ignored -> new Totals())
                        .add(
                                balance.getQuantityOnHand(),
                                balance.getQuantityReserved(),
                                balance.getQuantityBlocked());
            }
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
                    .map(variantId -> StockVariantBatchResponse.empty(variantId, request.warehouseId()))
                    .toList();
        }
        for (InventoryItem item : itemByVariantId.values()) {
            if (item.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("variantIds không tồn tại");
            }
        }

        List<InventoryBalance> balances = balanceRepository.findSellableBatchBalancesByInventoryItemIds(
                itemByVariantId.values().stream().map(InventoryItem::getId).toList(),
                InventoryStatus.ACTIVE,
                InventoryLocationSaleability.nonSaleableTypes(),
                LocalDate.now()
        );

        Map<UUID, VariantTotals> totalsByItemId = sumBalancesByItemId(balances);

        return variantIds.stream()
                .map(variantId -> {
                    InventoryItem item = itemByVariantId.get(variantId);
                    VariantTotals totals = item == null ? null : totalsByItemId.get(item.getId());
                    return toVariantResponse(variantId, totals, request.warehouseId());
                })
                .toList();
    }

    private StockBatchResponse toResponse(UUID productId, Totals totals) {
        if (totals == null) {
            return new StockBatchResponse(productId, ZERO, ZERO, "OUT_OF_STOCK");
        }
        BigDecimal available = totals.onHand.subtract(totals.reserved).subtract(totals.blocked);
        if (available.compareTo(ZERO) < 0) {
            available = ZERO;
        }
        return new StockBatchResponse(
                productId,
                available,
                totals.reserved,
                available.compareTo(ZERO) > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
    }

    private StockVariantBatchResponse toVariantResponse(UUID variantId, VariantTotals totals, UUID warehouseId) {
        if (totals == null) {
            return StockVariantBatchResponse.empty(variantId, warehouseId);
        }
        return new StockVariantBatchResponse(
                variantId,
                totals.bestLocationId(),
                totals.available,
                totals.reserved,
                totals.available.compareTo(ZERO) > 0 ? "IN_STOCK" : "OUT_OF_STOCK",
                totals.shelf,
                totals.warehouse,
                totals.bestLocationId(InventoryLocationType.SHELF),
                totals.bestWarehouseLocationId(warehouseId));
    }

    /**
     * 1 item có nhiều dòng tồn (mỗi batch trên mỗi kệ là 1 dòng) nên số lượng phải cộng dồn toàn bộ.
     */
    private Map<UUID, VariantTotals> sumBalancesByItemId(List<InventoryBalance> balances) {
        Map<UUID, VariantTotals> totalsByItemId = new HashMap<>();
        for (InventoryBalance balance : balances) {
            if (!InventoryLocationSaleability.isSaleable(balance.getLocation())
                    || isDetachedShelf(balance.getLocation())) {
                continue;
            }
            totalsByItemId.computeIfAbsent(balance.getInventoryItem().getId(), ignored -> new VariantTotals())
                    .add(balance);
        }
        return totalsByItemId;
    }

    /**
     * Bán tại quầy chỉ trừ trên các kệ nằm trực tiếp dưới một kho đang hoạt động (SaleFulfillmentServiceImpl),
     * nên kệ không thuộc kho nào không được tính vào số "trên kệ" của POS.
     */
    private static boolean isDetachedShelf(InventoryLocation location) {
        if (location.getType() != InventoryLocationType.SHELF) {
            return false;
        }
        InventoryLocation parent = location.getParentLocation();
        return parent == null
                || parent.getType() != InventoryLocationType.WAREHOUSE
                || parent.getStatus() != InventoryStatus.ACTIVE;
    }

    private static class VariantTotals {
        private BigDecimal available = ZERO;
        private BigDecimal reserved = ZERO;
        // Tách riêng số trên kệ (SHELF) và số trong kho (WAREHOUSE) để POS hiển thị hai con số.
        private BigDecimal shelf = ZERO;
        private BigDecimal warehouse = ZERO;
        private final Map<UUID, BigDecimal> availableByLocationId = new LinkedHashMap<>();
        private final Map<UUID, InventoryLocationType> typeByLocationId = new HashMap<>();

        private void add(InventoryBalance balance) {
            BigDecimal rowAvailable = balance.getAvailableQuantity();
            available = available.add(rowAvailable);
            reserved = reserved.add(balance.getQuantityReserved() != null ? balance.getQuantityReserved() : ZERO);
            UUID locationId = balance.getLocation().getId();
            InventoryLocationType type = balance.getLocation().getType();
            availableByLocationId.merge(locationId, rowAvailable, BigDecimal::add);
            typeByLocationId.put(locationId, type);
            if (type == InventoryLocationType.SHELF) {
                shelf = shelf.add(rowAvailable);
            } else {
                warehouse = warehouse.add(rowAvailable);
            }
        }

        // Response chỉ mang 1 locationId nên trả về vị trí đang còn nhiều hàng nhất.
        private UUID bestLocationId() {
            return availableByLocationId.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        private UUID bestLocationId(InventoryLocationType type) {
            return availableByLocationId.entrySet().stream()
                    .filter(entry -> typeByLocationId.get(entry.getKey()) == type)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        private UUID bestWarehouseLocationId(UUID fallback) {
            UUID best = availableByLocationId.entrySet().stream()
                    .filter(entry -> typeByLocationId.get(entry.getKey()) != InventoryLocationType.SHELF)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            return best != null ? best : fallback;
        }
    }

    private static class Totals {
        private BigDecimal onHand = ZERO;
        private BigDecimal reserved = ZERO;
        private BigDecimal blocked = ZERO;

        private Totals add(
                BigDecimal quantityOnHand,
                BigDecimal quantityReserved,
                BigDecimal quantityBlocked) {
            onHand = onHand.add(quantityOnHand != null ? quantityOnHand : ZERO);
            reserved = reserved.add(quantityReserved != null ? quantityReserved : ZERO);
            blocked = blocked.add(quantityBlocked != null ? quantityBlocked : ZERO);
            return this;
        }
    }
}
