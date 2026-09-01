package org.hvtra.iventoryservice.item.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.item.mapper.InventoryItemMapper;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryItemServiceImpl implements InventoryItemService {
    private final InventoryItemRepository repository;
    private final InventoryItemMapper mapper;

    public InventoryItemServiceImpl(
            InventoryItemRepository repository,
            InventoryItemMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<InventoryItemResponse> findAll(
            UUID referenceId,
            String referenceCode,
            InventoryItemType itemType,
            InventoryStatus status,
            Pageable pageable) {
        Specification<InventoryItem> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (referenceId != null) predicates.add(cb.equal(root.get("referenceId"), referenceId));
            if (referenceCode != null && !referenceCode.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("referenceCode")), "%" + referenceCode.trim().toLowerCase() + "%"));
            }
            if (itemType != null) predicates.add(cb.equal(root.get("itemType"), itemType));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(specification, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryItemResponse findById(UUID id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public InventoryItemResponse create(InventoryItemRequest request) {
        validateUnique(request, null);
        InventoryItem item = new InventoryItem();
        mapper.update(item, request);
        return mapper.toResponse(repository.save(item));
    }

    @Override
    @Transactional
    public InventoryItemResponse update(UUID id, InventoryItemRequest request) {
        validateUnique(request, id);
        InventoryItem item = get(id);
        mapper.update(item, request);
        return mapper.toResponse(repository.save(item));
    }

    @Override
    @Transactional
    public InventoryItemResponse updateByReference(
            UUID referenceId, InventoryItemType itemType, InventoryItemRequest request) {
        InventoryItem item = repository.findByReferenceIdAndItemType(referenceId, itemType)
                .orElseGet(() -> {
                    InventoryItem newItem = new InventoryItem();
                    newItem.setReferenceId(referenceId);
                    newItem.setItemType(itemType);
                    return newItem;
                });
        mapper.update(item, request);
        item.setReferenceId(referenceId);
        item.setItemType(itemType);
        return mapper.toResponse(repository.save(item));
    }

    @Override
    @Transactional
    public InventoryItemResponse updateStatus(UUID id, InventoryStatus status) {
        InventoryItem item = get(id);
        item.setStatus(status);
        return mapper.toResponse(repository.save(item));
    }

    private void validateUnique(InventoryItemRequest request, UUID id) {
        boolean exists = id == null
                ? repository.existsByReferenceIdAndItemType(request.referenceId(), request.itemType())
                : repository.existsByReferenceIdAndItemTypeAndIdNot(
                        request.referenceId(), request.itemType(), id);
        if (exists) {
            throw new BadRequestException(
                    "Inventory item already exists for reference "
                            + request.itemType() + ":" + request.referenceId());
        }
    }

    private InventoryItem get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }
}
