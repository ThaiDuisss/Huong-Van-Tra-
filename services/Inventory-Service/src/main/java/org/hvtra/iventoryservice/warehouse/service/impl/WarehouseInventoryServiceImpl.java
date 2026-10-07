package org.hvtra.iventoryservice.warehouse.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.warehouse.dto.LocationInventoryDetailResponse;
import org.hvtra.iventoryservice.warehouse.dto.WarehouseInventorySummaryResponse;
import org.hvtra.iventoryservice.warehouse.service.WarehouseInventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class WarehouseInventoryServiceImpl implements WarehouseInventoryService {

    private final org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository balanceRepository;
    private final CatalogClient catalogClient;

    @Override
    public Page<WarehouseInventorySummaryResponse> getProductInventorySummary(
        UUID branchId,
        String keyword,
        String itemType,
        Pageable pageable
    ) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("location").get("branchId"), branchId));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("inventoryItem").get("referenceCode")), pattern),
                    cb.like(cb.lower(root.get("inventoryItem").get("name")), pattern)
                ));
            }

            if (itemType != null && !itemType.isBlank() && !"ALL".equalsIgnoreCase(itemType)) {
                predicates.add(cb.equal(root.get("inventoryItem").get("itemType"), itemType));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var balances = balanceRepository.findAll(spec);

        // Group by inventoryItem to create summary
        Map<UUID, List<InventoryBalance>> groupedByItem = balances.stream()
            .collect(Collectors.groupingBy(b -> b.getInventoryItem().getId()));

        // Collect all unit IDs to fetch in batch
        var unitIds = groupedByItem.values().stream()
            .map(balances1 -> balances1.get(0).getInventoryItem().getUnitId())
            .filter(uuid -> uuid != null)
            .distinct()
            .collect(Collectors.toList());

        // Fetch all units in batch
        Map<UUID, CatalogClient.UnitResponse> unitMap = new java.util.HashMap<>();
        if (!unitIds.isEmpty()) {
            var units = catalogClient.getUnitsBatch(unitIds);
            for (var unit : units) {
                unitMap.put(unit.id(), unit);
            }
        }

        List<WarehouseInventorySummaryResponse> summaries = groupedByItem.entrySet().stream()
            .map(entry -> createSummary(entry.getValue(), unitMap))
            .collect(Collectors.toList());

        // Manual pagination
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), summaries.size());

        List<WarehouseInventorySummaryResponse> paged = summaries.subList(fromIndex, toIndex);
        return new PageImpl<>(paged, pageable, summaries.size());
    }

    @Override
    public List<LocationInventoryDetailResponse> getProductLocationInventory(UUID itemId) {
        Specification<InventoryBalance> spec = (root, query, cb) ->
            cb.equal(root.get("inventoryItem").get("id"), itemId);

        var balances = balanceRepository.findAll(spec);

        final String unitSymbol = getUnitSymbolFromBalance(balances);

        // Group by location
        Map<UUID, List<InventoryBalance>> groupedByLocation = balances.stream()
            .collect(Collectors.groupingBy(b -> b.getLocation().getId()));

        return groupedByLocation.entrySet().stream()
            .map(entry -> createLocationDetail(entry.getValue(), unitSymbol))
            .collect(Collectors.toList());
    }

    private WarehouseInventorySummaryResponse createSummary(
        List<InventoryBalance> balances,
        Map<UUID, CatalogClient.UnitResponse> unitMap
    ) {
        var item = balances.get(0).getInventoryItem();

        BigDecimal totalOnHand = BigDecimal.ZERO;
        BigDecimal shelfQuantity = BigDecimal.ZERO;
        BigDecimal warehouseQuantity = BigDecimal.ZERO;

        for (InventoryBalance balance : balances) {
            BigDecimal qty = balance.getQuantityOnHand();
            totalOnHand = totalOnHand.add(qty);

            String locationType = balance.getLocation().getType().name();
            if ("SHELF".equals(locationType)) {
                shelfQuantity = shelfQuantity.add(qty);
            } else if ("WAREHOUSE".equals(locationType)) {
                warehouseQuantity = warehouseQuantity.add(qty);
            }
        }

        BigDecimal reorderPoint = item.getReorderPoint() != null
            ? new BigDecimal(item.getReorderPoint())
            : BigDecimal.ZERO;
        String stockStatus = getStockStatus(totalOnHand, reorderPoint);

        String unitSymbol = null;
        if (item.getUnitId() != null && unitMap.containsKey(item.getUnitId())) {
            unitSymbol = unitMap.get(item.getUnitId()).symbol();
        }

        return new WarehouseInventorySummaryResponse(
            item.getId().toString(),
            item.getReferenceCode(),
            item.getName(),
            "-",
            "-",
            "-",
            unitSymbol,
            BigDecimal.ZERO,
            totalOnHand,
            shelfQuantity,
            warehouseQuantity,
            reorderPoint,
            stockStatus
        );
    }

    private LocationInventoryDetailResponse createLocationDetail(
        List<InventoryBalance> balances,
        String unitSymbol
    ) {
        var location = balances.get(0).getLocation();

        BigDecimal totalOnHand = BigDecimal.ZERO;
        BigDecimal totalReserved = BigDecimal.ZERO;
        BigDecimal totalBlocked = BigDecimal.ZERO;
        BigDecimal totalAvailable = BigDecimal.ZERO;
        long batchCount = 0;

        for (InventoryBalance balance : balances) {
            totalOnHand = totalOnHand.add(balance.getQuantityOnHand());
            totalReserved = totalReserved.add(balance.getQuantityReserved());
            totalBlocked = totalBlocked.add(balance.getQuantityBlocked());
            totalAvailable = totalAvailable.add(balance.getAvailableQuantity());
            batchCount++;
        }

        return new LocationInventoryDetailResponse(
            location.getId(),
            location.getName(),
            location.getType().name(),
            unitSymbol,
            totalOnHand,
            totalReserved,
            totalBlocked,
            totalAvailable,
            batchCount
        );
    }

    private String getUnitSymbolFromBalance(List<InventoryBalance> balances) {
        if (!balances.isEmpty()) {
            var item = balances.get(0).getInventoryItem();
            if (item.getUnitId() != null) {
                try {
                    return catalogClient.getUnitsBatch(List.of(item.getUnitId())).stream()
                            .findFirst()
                            .map(CatalogClient.UnitResponse::symbol)
                            .orElse(null);
                } catch (Exception e) {
                    log.error("Exception occurred while processing request", e);
                }
            }
        }
        return null;
    }

    private String getStockStatus(BigDecimal quantity, BigDecimal reorderPoint) {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return "OUT_OF_STOCK";
        }
        if (quantity.compareTo(reorderPoint) <= 0) {
            return "LOW_STOCK";
        }
        return "SUFFICIENT";
    }
}
