package org.hvtra.iventoryservice.warehouse.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
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
public class WarehouseInventoryServiceImpl implements WarehouseInventoryService {

    private final org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository balanceRepository;

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

        List<WarehouseInventorySummaryResponse> summaries = groupedByItem.entrySet().stream()
            .map(entry -> createSummary(entry.getValue()))
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

        // Group by location
        Map<UUID, List<InventoryBalance>> groupedByLocation = balances.stream()
            .collect(Collectors.groupingBy(b -> b.getLocation().getId()));

        return groupedByLocation.entrySet().stream()
            .map(entry -> createLocationDetail(entry.getValue()))
            .collect(Collectors.toList());
    }

    private WarehouseInventorySummaryResponse createSummary(List<InventoryBalance> balances) {
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

        return new WarehouseInventorySummaryResponse(
            item.getId().toString(),
            item.getReferenceCode(),
            item.getName(),
            "-",
            "-",
            "-",
            BigDecimal.ZERO,
            totalOnHand,
            shelfQuantity,
            warehouseQuantity,
            reorderPoint,
            stockStatus
        );
    }

    private LocationInventoryDetailResponse createLocationDetail(List<InventoryBalance> balances) {
        var location = balances.get(0).getLocation();

        BigDecimal totalOnHand = BigDecimal.ZERO;
        BigDecimal totalReserved = BigDecimal.ZERO;
        BigDecimal totalBlocked = BigDecimal.ZERO;
        long batchCount = 0;

        for (InventoryBalance balance : balances) {
            totalOnHand = totalOnHand.add(balance.getQuantityOnHand());
            totalReserved = totalReserved.add(balance.getQuantityReserved());
            totalBlocked = totalBlocked.add(balance.getQuantityBlocked());
            batchCount++;
        }

        BigDecimal available = totalOnHand.subtract(totalReserved).subtract(totalBlocked);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            available = BigDecimal.ZERO;
        }

        return new LocationInventoryDetailResponse(
            location.getId(),
            location.getName(),
            location.getType().name(),
            totalOnHand,
            totalReserved,
            totalBlocked,
            available,
            batchCount
        );
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
