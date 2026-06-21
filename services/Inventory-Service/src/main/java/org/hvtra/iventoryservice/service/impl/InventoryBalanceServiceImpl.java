package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.InventoryBalanceMapper;
import org.hvtra.iventoryservice.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryBalanceServiceImpl implements InventoryBalanceService {
    private final InventoryBalanceRepository repository;
    private final InventoryItemRepository itemRepository;
    private final InventoryBalanceMapper mapper;

    public InventoryBalanceServiceImpl(
            InventoryBalanceRepository repository,
            InventoryItemRepository itemRepository,
            InventoryBalanceMapper mapper) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<InventoryBalanceResponse> findAll(
            UUID inventoryItemId, UUID locationId, UUID branchId, Pageable pageable) {
        return find(inventoryItemId, locationId, branchId, pageable);
    }

    @Override
    public InventoryBalanceResponse findById(UUID id) {
        InventoryBalance balance = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory balance", id));
        return mapper.toResponse(balance);
    }

    @Override
    public PageResponse<InventoryBalanceResponse> findByInventoryItem(
            UUID itemId, Pageable pageable) {
        if (!itemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("Inventory item", itemId);
        }
        return find(itemId, null, null, pageable);
    }

    private PageResponse<InventoryBalanceResponse> find(
            UUID inventoryItemId, UUID locationId, UUID branchId, Pageable pageable) {
        Specification<InventoryBalance> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (inventoryItemId != null) {
                predicates.add(cb.equal(root.get("inventoryItem").get("id"), inventoryItemId));
            }
            if (locationId != null) predicates.add(cb.equal(root.get("location").get("id"), locationId));
            if (branchId != null) {
                predicates.add(cb.equal(root.get("location").get("branchId"), branchId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(specification, pageable).map(mapper::toResponse));
    }
}
