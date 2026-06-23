package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.InventoryLocation;
import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.InventoryLocationMapper;
import org.hvtra.iventoryservice.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.service.InventoryLocationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryLocationServiceImpl implements InventoryLocationService {
    private final InventoryLocationRepository repository;
    private final InventoryLocationMapper mapper;

    public InventoryLocationServiceImpl(
            InventoryLocationRepository repository, InventoryLocationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<InventoryLocationResponse> findAll(
            UUID branchId,
            InventoryLocationType type,
            InventoryStatus status,
            Pageable pageable) {
        Specification<InventoryLocation> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(specification, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryLocationResponse findById(UUID id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public InventoryLocationResponse create(InventoryLocationRequest request) {
        validateCode(request, null);
        InventoryLocation location = new InventoryLocation();
        mapper.update(location, request, resolveParent(request, null));
        return mapper.toResponse(repository.save(location));
    }

    @Override
    @Transactional
    public InventoryLocationResponse update(UUID id, InventoryLocationRequest request) {
        validateCode(request, id);
        InventoryLocation location = get(id);
        mapper.update(location, request, resolveParent(request, id));
        return mapper.toResponse(repository.save(location));
    }

    @Override
    @Transactional
    public InventoryLocationResponse updateStatus(UUID id, InventoryStatus status) {
        InventoryLocation location = get(id);
        location.setStatus(status);
        return mapper.toResponse(repository.save(location));
    }

    private InventoryLocation resolveParent(InventoryLocationRequest request, UUID locationId) {
        if (request.parentLocationId() == null) return null;
        if (request.parentLocationId().equals(locationId)) {
            throw new BadRequestException("Inventory location cannot be its own parent");
        }
        InventoryLocation parent = get(request.parentLocationId());
//        if (!parent.getBranchId().equals(request.branchId())) {
//            throw new BadRequestException("Parent location must belong to the same branch");
//        }
        for (InventoryLocation current = parent;
             current != null;
             current = current.getParentLocation()) {
            if (current.getId().equals(locationId)) {
                throw new BadRequestException("Inventory location hierarchy cannot contain a cycle");
            }
        }
        return parent;
    }

    private void validateCode(InventoryLocationRequest request, UUID id) {
        String code = request.code().trim();
//        boolean exists = id == null
//                ? repository.existsByBranchIdAndCode(request.branchId(), code)
//                : repository.existsByBranchIdAndCodeAndIdNot(request.branchId(), code, id);
//        if (exists) throw new BadRequestException("Location code already exists in branch: " + code);
    }

    private InventoryLocation get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory location", id));
    }
}
