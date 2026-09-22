package org.hvtra.iventoryservice.location.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.location.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.location.mapper.InventoryLocationMapper;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.location.service.StoreShelfService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hvtra.commonservice.util.CodeUtils;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;
import org.springframework.data.domain.Page;

@Service
@Transactional(readOnly = true)
public class StoreShelfServiceImpl implements StoreShelfService {

    private final InventoryLocationRepository repository;
    private final InventoryLocationMapper mapper;

    public StoreShelfServiceImpl(
            InventoryLocationRepository repository,
            InventoryLocationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PageResponse<LookupResponse> lookup(String search, Pageable pageable) {
        Page<InventoryLocation> page = repository.findByNameContainingIgnoreCaseAndTypeAndIsDeletedFalse(search, InventoryLocationType.SHELF, pageable);
        return new PageResponse<>(
                page.getContent().stream()
                        .map(shelf -> new LookupResponse(shelf.getId().toString(), shelf.getName()))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public PageResponse<InventoryLocationResponse> findAll(
            String query,
            InventoryStatus status,
            Pageable pageable) {

        InventoryLocation warehouse = resolveWarehouse();

        Specification<InventoryLocation> spec = (root, rootQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            // Chỉ lấy kệ (SHELF) thuộc kho đầu tiên
            predicates.add(cb.equal(root.get("type"), InventoryLocationType.SHELF));
            predicates.add(cb.equal(root.get("parentLocation"), warehouse));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return PageResponse.from(
                repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryLocationResponse findById(UUID id) {
        return mapper.toResponse(getShelf(id));
    }

    @Override
    @Transactional
    public InventoryLocationResponse create(StoreShelfRequest request) {
        InventoryLocation warehouse = resolveWarehouse();

        InventoryLocation shelf = new InventoryLocation();
        shelf.setBranchId(warehouse.getBranchId());          // kế thừa branchId từ kho
        shelf.setCode(generateShelfCode(request.name()));
        shelf.setName(request.name().trim());
        shelf.setType(InventoryLocationType.SHELF);
        shelf.setStatus(request.status());
        shelf.setParentLocation(warehouse);

        return mapper.toResponse(repository.save(shelf));
    }

    @Override
    @Transactional
    public InventoryLocationResponse update(UUID id, StoreShelfRequest request) {
        InventoryLocation shelf = getShelf(id);
        shelf.setName(request.name().trim());
        shelf.setStatus(request.status());
        // type và parentLocation không được thay đổi
        return mapper.toResponse(repository.save(shelf));
    }

    @Override
    @Transactional
    public InventoryLocationResponse updateStatus(UUID id, InventoryStatus status) {
        InventoryLocation shelf = getShelf(id);
        shelf.setStatus(status);
        return mapper.toResponse(repository.save(shelf));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trả về kho WAREHOUSE đầu tiên trong hệ thống (tạm thời chưa có xác thực người dùng).
     */
    private InventoryLocation resolveWarehouse() {
        return repository
                .findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy kho WAREHOUSE nào đang hoạt động", null));
    }

    /**
     * Lấy kệ theo id, kiểm tra đúng loại SHELF.
     */
    private InventoryLocation getShelf(UUID id) {
        InventoryLocation location = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kệ", id));
        if (location.getType() != InventoryLocationType.SHELF) {
            throw new ResourceNotFoundException("Kệ", id);
        }
        return location;
    }

    /**
     * Sinh mã kệ tự động: {Viết tắt tên kệ}-{4 số ngẫu nhiên duy nhất}.
     * Ví dụ: KE-1024, KT1-8192
     */
    private String generateShelfCode(String shelfName) {
        String prefix = CodeUtils.getInitials(shelfName, "KE");
        String code;
        do {
            int randomNum = ThreadLocalRandom.current().nextInt(1000, 10000);
            code = String.format("%s-%04d", prefix, randomNum);
        } while (repository.existsByCode(code));
        return code;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        InventoryLocation shelf = getShelf(id);
        if (shelf.getIsDeleted()) {
            throw new ResourceNotFoundException("Kệ", id);
        }
        shelf.setIsDeleted(true);
        repository.save(shelf);
    }
}
