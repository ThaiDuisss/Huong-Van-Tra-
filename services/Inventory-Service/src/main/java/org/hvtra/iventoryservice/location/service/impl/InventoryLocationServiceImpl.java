package org.hvtra.iventoryservice.location.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.location.mapper.InventoryLocationMapper;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.location.service.InventoryLocationService;
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
            String query,
            InventoryLocationType type,
            InventoryStatus status,
            Pageable pageable) {
        Specification<InventoryLocation> specification = (root, rootQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)
                ));
            }
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
        InventoryLocation location = new InventoryLocation();
        String code = generateCode(request.type());
        location.setCode(code);
        mapper.update(location, request, resolveParent(request, null));
        return mapper.toResponse(repository.save(location));
    }

    @Override
    @Transactional
    public InventoryLocationResponse update(UUID id, InventoryLocationRequest request) {
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

    /**
     * Sinh mã vị trí tự động dựa theo loại.
     * Cấu trúc: {PREFIX}-{8 ký tự hex ngẫu nhiên viết hoa}
     * Ví dụ: KHO-A3F9B2C1, KC-7E2D401F, HL-C5080BEE, KE-4D12A8F0
     */
    private String generateCode(InventoryLocationType type) {
        String prefix = switch (type) {
            case WAREHOUSE -> "KHO";
            case STORAGE   -> "KC";
            case DAMAGED   -> "HL";
            case SHELF     -> "KE";
            case QUARANTINE -> "CL";
        };
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return prefix + "-" + suffix;
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

    private InventoryLocation get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory location", id));
    }

    @Override
    public InventoryLocationResponse findDefaultWarehouse(UUID branchId) {
        InventoryLocation location = repository.findFirstByBranchIdAndTypeAndStatus(
                branchId,
                InventoryLocationType.WAREHOUSE,
                InventoryStatus.ACTIVE
        )
        .or(() -> repository.findFirstByTypeAndStatus(
                InventoryLocationType.WAREHOUSE,
                InventoryStatus.ACTIVE
        ))
        .or(() -> repository.findFirstByType(
                InventoryLocationType.WAREHOUSE
        ))
        .orElseThrow(() -> new ResourceNotFoundException(
                "No WAREHOUSE location found at all", null));
        return mapper.toResponse(location);
    }
}
