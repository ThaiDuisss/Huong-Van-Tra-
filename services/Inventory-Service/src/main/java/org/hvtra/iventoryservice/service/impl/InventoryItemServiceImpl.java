package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.InventoryItem;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.InventoryItemMapper;
import org.hvtra.iventoryservice.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.service.InventoryItemService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class InventoryItemServiceImpl implements InventoryItemService {
    private final InventoryItemRepository repository;
    private final InventoryItemMapper mapper;

    public InventoryItemServiceImpl(InventoryItemRepository repository, InventoryItemMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<InventoryItemResponse> findAll(
            Long referenceId,
            InventoryItemType itemType,
            InventoryStatus status,
            Pageable pageable) {
        Specification<InventoryItem> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (referenceId != null) predicates.add(cb.equal(root.get("referenceId"), referenceId));
            if (itemType != null) predicates.add(cb.equal(root.get("itemType"), itemType));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(specification, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryItemResponse findById(Long id) {
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
    public InventoryItemResponse update(Long id, InventoryItemRequest request) {
        validateUnique(request, id);
        InventoryItem item = get(id);
        mapper.update(item, request);
        return mapper.toResponse(repository.save(item));
    }

    @Override
    @Transactional
    public InventoryItemResponse updateStatus(Long id, InventoryStatus status) {
        InventoryItem item = get(id);
        item.setStatus(status);
        return mapper.toResponse(repository.save(item));
    }

    private void validateUnique(InventoryItemRequest request, Long id) {
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

    private InventoryItem get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }
}
