package org.hvtra.iventoryservice.replenishmentpolicy.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentPolicy;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.request.ReplenishmentPolicyRequest;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyMatrixRowResponse;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyResponse;
import org.hvtra.iventoryservice.replenishmentpolicy.repository.InventoryReplenishmentPolicyRepository;
import org.hvtra.iventoryservice.replenishmentpolicy.service.ReplenishmentPolicyService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplenishmentPolicyServiceImpl implements ReplenishmentPolicyService {

    private final InventoryReplenishmentPolicyRepository policyRepository;
    private final InventoryItemRepository itemRepository;

    @Override
    public PageResponse<ReplenishmentPolicyResponse> findAll(
            UUID inventoryItemId,
            InventoryLocationType locationType,
            InventoryItemType itemType,
            Boolean enabled,
            String search,
            Pageable pageable
    ) {
        Specification<InventoryReplenishmentPolicy> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            Join<InventoryReplenishmentPolicy, InventoryItem> itemJoin = root.join("inventoryItem");

            if (inventoryItemId != null) {
                predicates.add(cb.equal(itemJoin.get("id"), inventoryItemId));
            }
            if (locationType != null) {
                predicates.add(cb.equal(root.get("locationType"), locationType));
            }
            if (itemType != null) {
                predicates.add(cb.equal(itemJoin.get("itemType"), itemType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(itemJoin.get("name")), pattern),
                        cb.like(cb.lower(itemJoin.get("referenceCode")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<InventoryReplenishmentPolicy> page = policyRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    public PageResponse<ReplenishmentPolicyMatrixRowResponse> findMatrix(
            InventoryItemType itemType,
            String search,
            Pageable pageable
    ) {
        String keyword = search != null && !search.isBlank() ? search.trim() : null;
        Page<InventoryItem> itemsPage = itemRepository.lookupActiveItems(keyword, itemType, pageable);

        List<UUID> itemIds = itemsPage.getContent().stream()
                .map(InventoryItem::getId)
                .toList();

        Map<UUID, Map<InventoryLocationType, InventoryReplenishmentPolicy>> policiesByItem = itemIds.isEmpty()
                ? Map.of()
                : policyRepository.findByInventoryItemIdInAndIsDeletedFalse(itemIds).stream()
                        .collect(Collectors.groupingBy(
                                p -> p.getInventoryItem().getId(),
                                Collectors.toMap(
                                        InventoryReplenishmentPolicy::getLocationType,
                                        p -> p,
                                        (a, b) -> a,
                                        () -> new EnumMap<>(InventoryLocationType.class)
                                )
                        ));

        return PageResponse.from(itemsPage.map(item -> {
            Map<InventoryLocationType, InventoryReplenishmentPolicy> map =
                    policiesByItem.getOrDefault(item.getId(), Map.of());
            return new ReplenishmentPolicyMatrixRowResponse(
                    item.getId(),
                    item.getReferenceCode(),
                    item.getName(),
                    item.getItemType(),
                    toCell(map.get(InventoryLocationType.WAREHOUSE)),
                    toCell(map.get(InventoryLocationType.SHELF))
            );
        }));
    }

    private ReplenishmentPolicyMatrixRowResponse.PolicyCell toCell(InventoryReplenishmentPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new ReplenishmentPolicyMatrixRowResponse.PolicyCell(
                policy.getId(),
                policy.getMinimumStock(),
                policy.getMaximumStock(),
                policy.getEnabled()
        );
    }

    @Override
    public ReplenishmentPolicyResponse findById(UUID id) {
        InventoryReplenishmentPolicy policy = policyRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryReplenishmentPolicy", id));
        return toResponse(policy);
    }

    @Override
    @Transactional
    public ReplenishmentPolicyResponse create(ReplenishmentPolicyRequest request) {
        validateRequest(request);

        if (policyRepository.existsByInventoryItemIdAndLocationTypeAndIsDeletedFalse(
                request.inventoryItemId(), request.locationType())) {
            throw new BadRequestException("Đã tồn tại chính sách ngưỡng tồn cho mặt hàng tại loại vị trí này");
        }

        InventoryItem item = itemRepository.findById(request.inventoryItemId())
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", request.inventoryItemId()));
        validateItemLocationCompatibility(item, request.locationType());

        InventoryReplenishmentPolicy policy = new InventoryReplenishmentPolicy();
        applyRequest(policy, item, request);
        return toResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional
    public ReplenishmentPolicyResponse update(UUID id, ReplenishmentPolicyRequest request) {
        validateRequest(request);

        InventoryReplenishmentPolicy policy = policyRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryReplenishmentPolicy", id));

        if (policyRepository.existsByInventoryItemIdAndLocationTypeAndIsDeletedFalseAndIdNot(
                request.inventoryItemId(), request.locationType(), id)) {
            throw new BadRequestException("Đã tồn tại chính sách ngưỡng tồn cho mặt hàng tại loại vị trí này");
        }

        InventoryItem item = itemRepository.findById(request.inventoryItemId())
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", request.inventoryItemId()));
        validateItemLocationCompatibility(item, request.locationType());

        applyRequest(policy, item, request);
        return toResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional
    public ReplenishmentPolicyResponse toggleEnabled(UUID id, boolean enabled) {
        InventoryReplenishmentPolicy policy = policyRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryReplenishmentPolicy", id));
        policy.setEnabled(enabled);
        return toResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        InventoryReplenishmentPolicy policy = policyRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryReplenishmentPolicy", id));
        policy.setIsDeleted(true);
        policyRepository.save(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveEffectiveMinimumStock(UUID inventoryItemId, InventoryLocationType locationType) {
        if (inventoryItemId == null) {
            return BigDecimal.ZERO;
        }
        if (locationType != null) {
            InventoryReplenishmentPolicy policy = policyRepository
                    .findByInventoryItemIdAndLocationTypeAndIsDeletedFalse(inventoryItemId, locationType)
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .orElse(null);
            if (policy != null && policy.getMinimumStock() != null) {
                return policy.getMinimumStock();
            }
        }
        return itemRepository.findById(inventoryItemId)
                .map(InventoryItem::getReorderPoint)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);
    }

    private void validateItemLocationCompatibility(InventoryItem item, InventoryLocationType locationType) {
        if (item.getItemType() == InventoryItemType.INGREDIENT
                && locationType == InventoryLocationType.SHELF) {
            throw new BadRequestException("Nguyên liệu chỉ được cấu hình ngưỡng tồn Trong Kho (WAREHOUSE)");
        }
    }

    private void validateRequest(ReplenishmentPolicyRequest request) {
        if (request.locationType() != InventoryLocationType.WAREHOUSE
                && request.locationType() != InventoryLocationType.SHELF) {
            throw new BadRequestException("Chỉ hỗ trợ cấu hình ngưỡng tồn cho Kho (WAREHOUSE) hoặc Kệ (SHELF)");
        }
        if (request.maximumStock() != null && request.maximumStock().compareTo(request.minimumStock()) <= 0) {
            throw new BadRequestException("Tồn tối đa phải lớn hơn tồn tối thiểu");
        }
    }

    private void applyRequest(
            InventoryReplenishmentPolicy policy,
            InventoryItem item,
            ReplenishmentPolicyRequest request
    ) {
        policy.setInventoryItem(item);
        policy.setLocationType(request.locationType());
        policy.setMinimumStock(request.minimumStock());
        policy.setMaximumStock(request.maximumStock());
        policy.setEnabled(request.enabled());
    }

    private ReplenishmentPolicyResponse toResponse(InventoryReplenishmentPolicy policy) {
        InventoryItem item = policy.getInventoryItem();
        return new ReplenishmentPolicyResponse(
                policy.getId(),
                item.getId(),
                item.getReferenceCode(),
                item.getName(),
                item.getItemType(),
                policy.getLocationType(),
                policy.getMinimumStock(),
                policy.getMaximumStock(),
                policy.getEnabled(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
