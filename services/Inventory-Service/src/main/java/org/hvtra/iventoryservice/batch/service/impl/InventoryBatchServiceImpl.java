package org.hvtra.iventoryservice.batch.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.batch.service.InventoryBatchService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.dto.response.WarehouseBatchResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class InventoryBatchServiceImpl implements InventoryBatchService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final CatalogClient catalogClient;

    @Override
    public List<WarehouseBatchResponse> getBatchesByItemAndLocation(UUID itemId, UUID locationId) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("inventoryItem").get("id"), itemId));
            predicates.add(cb.equal(root.get("location").get("id"), locationId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var balances = balanceRepository.findAll(spec);
        final String unitSymbol = getUnitSymbolFromBalance(balances);

        return balances.stream()
                .map(balance -> mapToResponse(balance, unitSymbol))
                .sorted((a, b) -> {
                    // FEFO sort: null expiry dates last, then by expiry date ascending
                    if (a.expiryDate() == null && b.expiryDate() == null) return 0;
                    if (a.expiryDate() == null) return 1;
                    if (b.expiryDate() == null) return -1;
                    return a.expiryDate().compareTo(b.expiryDate());
                })
                .toList();
    }

    @Override
    public WarehouseBatchResponse getBatchDetail(UUID batchId, UUID locationId) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("inventoryBatch").get("id"), batchId));
            predicates.add(cb.equal(root.get("location").get("id"), locationId));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var balance = balanceRepository.findAll(spec).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBalance", batchId));

        final String unitSymbol = getUnitSymbolFromBalance(List.of(balance));
        return mapToResponse(balance, unitSymbol);
    }

    private String getUnitSymbolFromBalance(List<InventoryBalance> balances) {
        if (!balances.isEmpty()) {
            var item = balances.get(0).getInventoryItem();
            if (item.getUnitId() != null) {
                try {
                    return catalogClient.getUnit(item.getUnitId()).symbol();
                } catch (Exception e) {
                    log.error("Exception occurred while processing request", e);
                }
            }
        }
        return null;
    }

    private WarehouseBatchResponse mapToResponse(InventoryBalance balance, String unitSymbol) {
        var batch = balance.getInventoryBatch();
        var quantityOnHand = balance.getQuantityOnHand();
        var quantityReserved = balance.getQuantityReserved();
        var quantityBlocked = balance.getQuantityBlocked();
        var available = balance.getAvailableQuantity();

        Long daysUntilExpiry = null;
        if (batch.getExpiryDate() != null) {
            daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpiryDate());
        }

        return new WarehouseBatchResponse(
                batch.getId(),
                batch.getBatchCode(),
                batch.getSupplierBatchCode(),
                batch.getManufactureDate(),
                batch.getExpiryDate(),
                batch.getStatus().name(),
                unitSymbol,
                quantityOnHand,
                quantityReserved,
                quantityBlocked,
                available,
                daysUntilExpiry
        );
    }
}
