package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.client.CatalogClient;
import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.InventoryBalanceViewResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.InventoryBalance;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.InventoryBalanceMapper;
import org.hvtra.iventoryservice.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryBalanceServiceImpl implements InventoryBalanceService {
    private final InventoryBalanceRepository repository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceMapper mapper;
    private final CatalogClient catalogClient;

    public InventoryBalanceServiceImpl(
            InventoryBalanceRepository repository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceMapper mapper,
            CatalogClient catalogClient) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.mapper = mapper;
        this.catalogClient = catalogClient;
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

    @Override
    public PageResponse<InventoryBalanceViewResponse> findAllView(
            UUID branchId,
            UUID locationId,
            String sku,
            String productCode,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId,
            Pageable pageable
    ) {
        // 1. Resolve variant IDs if catalog filters are active
        List<UUID> matchingVariantIds = null;
        boolean hasCatalogFilters = (sku != null && !sku.isBlank()) ||
                                    (productCode != null && !productCode.isBlank()) ||
                                    (productName != null && !productName.isBlank()) ||
                                    (categoryCode != null && !categoryCode.isBlank()) ||
                                    categoryId != null ||
                                    unitId != null;

        if (hasCatalogFilters) {
            matchingVariantIds = catalogClient.searchVariantIds(sku, productCode, productName, categoryCode, categoryId, unitId);
            if (matchingVariantIds == null || matchingVariantIds.isEmpty()) {
                return new PageResponse<>(
                        java.util.Collections.emptyList(),
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        0,
                        0,
                        true,
                        true
                );
            }
        }

        // 2. Query local InventoryBalances with filters
        final List<UUID> finalMatchingVariantIds = matchingVariantIds;
        Specification<InventoryBalance> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("location").get("branchId"), branchId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (hasCatalogFilters && finalMatchingVariantIds != null) {
                predicates.add(root.get("inventoryItem").get("referenceId").in(finalMatchingVariantIds));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<InventoryBalance> balancePage = repository.findAll(specification, pageable);
        if (balancePage.isEmpty()) {
            return new PageResponse<>(
                    java.util.Collections.emptyList(),
                    balancePage.getNumber(),
                    balancePage.getSize(),
                    balancePage.getTotalElements(),
                    balancePage.getTotalPages(),
                    balancePage.isFirst(),
                    balancePage.isLast()
            );
        }

        // 3. Batch resolve catalog entities for mapped records in memory
        List<UUID> variantIds = balancePage.getContent().stream()
                .map(b -> b.getInventoryItem().getReferenceId())
                .distinct()
                .toList();

        List<CatalogClient.VariantResponse> variants = catalogClient.getVariantsBatch(variantIds);
        java.util.Map<UUID, CatalogClient.VariantResponse> variantMap = variants.stream()
                .collect(java.util.stream.Collectors.toMap(CatalogClient.VariantResponse::id, v -> v, (v1, v2) -> v1));

        List<UUID> productIds = variants.stream().map(CatalogClient.VariantResponse::productId).distinct().toList();
        List<CatalogClient.ProductResponse> products = catalogClient.getProductsBatch(productIds);
        java.util.Map<UUID, CatalogClient.ProductResponse> productMap = products.stream()
                .collect(java.util.stream.Collectors.toMap(CatalogClient.ProductResponse::id, p -> p, (p1, p2) -> p1));

        List<UUID> unitIds = variants.stream().map(CatalogClient.VariantResponse::baseUnitId).distinct().toList();
        List<CatalogClient.UnitResponse> units = catalogClient.getUnitsBatch(unitIds);
        java.util.Map<UUID, CatalogClient.UnitResponse> unitMap = units.stream()
                .collect(java.util.stream.Collectors.toMap(CatalogClient.UnitResponse::id, u -> u, (u1, u2) -> u1));

        // 4. Map and Join
        List<InventoryBalanceViewResponse> mappedList = balancePage.getContent().stream()
                .map(b -> {
                    CatalogClient.VariantResponse v = variantMap.get(b.getInventoryItem().getReferenceId());
                    CatalogClient.ProductResponse p = (v != null) ? productMap.get(v.productId()) : null;
                    CatalogClient.CategoryResponse cat = (p != null) ? p.category() : null;
                    CatalogClient.UnitResponse u = (v != null) ? unitMap.get(v.baseUnitId()) : null;
                    org.hvtra.iventoryservice.entity.InventoryLocation loc = b.getLocation();

                    return InventoryBalanceViewResponse.builder()
                            .inventoryBalanceId(b.getId())
                            .inventoryItemId(b.getInventoryItem().getId())
                            .productId(p != null ? p.id() : null)
                            .productCode(p != null ? p.productCode() : (v != null ? v.productCode() : null))
                            .productName(p != null ? p.name() : (v != null ? v.productName() : null))
                            .variantId(v != null ? v.id() : null)
                            .variantName(v != null ? v.variantName() : null)
                            .sku(v != null ? v.sku() : null)
                            .categoryId(cat != null ? cat.id() : null)
                            .categoryCode(cat != null ? cat.categoryCode() : null)
                            .categoryName(cat != null ? cat.name() : null)
                            .unitId(u != null ? u.id() : (v != null ? v.baseUnitId() : null))
                            .unitName(u != null ? u.name() : null)
                            .unitSymbol(u != null ? u.symbol() : null)
                            .locationId(loc != null ? loc.getId() : null)
                            .locationName(loc != null ? loc.getName() : null)
                            .branchId(loc != null ? loc.getBranchId() : null)
                            .imageUrl(p != null ? p.imageUrl() : null)
                            .costPrice(v != null ? v.costPrice() : null)
                            .sellingPrice(v != null ? v.sellingPrice() : null)
                            .quantityOnHand(b.getQuantityOnHand())
                            .quantityReserved(b.getQuantityReserved())
                            .availableQuantity(b.getQuantityOnHand().subtract(b.getQuantityReserved()))
                            .updatedAt(b.getUpdatedAt())
                            .build();
                })
                .toList();

        return new PageResponse<>(
                mappedList,
                balancePage.getNumber(),
                balancePage.getSize(),
                balancePage.getTotalElements(),
                balancePage.getTotalPages(),
                balancePage.isFirst(),
                balancePage.isLast()
        );
    }

    @Override
    public java.util.List<org.hvtra.iventoryservice.dto.response.InventoryBalanceWarehouseResponse> findWarehouseBalances(
            String sku,
            String productName
    ) {
        // 1. Get first location whose type is WAREHOUSE
        java.util.Optional<org.hvtra.iventoryservice.entity.InventoryLocation> warehouseLocOpt =
                locationRepository.findFirstByType(org.hvtra.iventoryservice.enums.InventoryLocationType.WAREHOUSE);
        if (warehouseLocOpt.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        org.hvtra.iventoryservice.entity.InventoryLocation warehouseLoc = warehouseLocOpt.get();

        // 2. Query matching variant IDs from Catalog Service if sku or productName is provided
        List<UUID> matchingVariantIds = null;
        if ((sku != null && !sku.isBlank()) || (productName != null && !productName.isBlank())) {
            matchingVariantIds = catalogClient.searchVariantIds(
                    sku != null ? sku.trim() : null,
                    null,
                    productName != null ? productName.trim() : null,
                    null,
                    null,
                    null
            );
            if (matchingVariantIds == null || matchingVariantIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
        }

        // 3. Find balances for that location, and filter by matching variant reference IDs if applicable
        final List<UUID> finalMatchingVariantIds = matchingVariantIds;
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("location").get("id"), warehouseLoc.getId()));
            if (finalMatchingVariantIds != null) {
                predicates.add(root.get("inventoryItem").get("referenceId").in(finalMatchingVariantIds));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<InventoryBalance> balances = repository.findAll(spec);

        return balances.stream()
                .map(b -> new org.hvtra.iventoryservice.dto.response.InventoryBalanceWarehouseResponse(
                        b.getId(),
                        b.getLocation().getId(),
                        b.getInventoryItem().getId(),
                        b.getInventoryItem().getReferenceId(),
                        b.getInventoryItem().getItemType(),
                        b.getQuantityOnHand(),
                        b.getQuantityReserved(),
                        b.getQuantityOnHand().subtract(b.getQuantityReserved()),
                        b.getInventoryItem().getStatus()
                ))
                .toList();
    }
}
