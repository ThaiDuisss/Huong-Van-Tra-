package org.hvtra.iventoryservice.batch.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.batch.service.InventoryBatchService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
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
public class InventoryBatchServiceImpl implements InventoryBatchService {

    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;

    @Override
    public List<WarehouseBatchResponse> getBatchesByItemAndLocation(UUID itemId, UUID locationId) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("inventoryItem").get("id"), itemId));
            predicates.add(cb.equal(root.get("location").get("id"), locationId));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var balances = balanceRepository.findAll(spec);

        return balances.stream()
            .map(balance -> mapToResponse(balance))
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

        return mapToResponse(balance);
    }

    private WarehouseBatchResponse mapToResponse(InventoryBalance balance) {
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
            quantityOnHand,
            quantityReserved,
            quantityBlocked,
            available,
            daysUntilExpiry
        );
    }
}
