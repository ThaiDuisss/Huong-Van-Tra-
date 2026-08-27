package org.hvtra.iventoryservice.stock.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.StockType;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceViewResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceWarehouseResponse;
import org.hvtra.iventoryservice.stock.mapper.InventoryBalanceMapper;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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
            String keyword,
            String sku,
            String productCode,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId,
            UUID locationId,
            UUID branchId,
            StockType status,
            Pageable pageable
    ) {
        // 1. Resolve variant IDs if catalog filters are active
        List<UUID> matchingVariantIds = null;
        boolean hasCatalogFilters = (keyword != null && !keyword.isBlank()) ||
                                    (sku != null && !sku.isBlank()) ||
                                    (productCode != null && !productCode.isBlank()) ||
                                    (productName != null && !productName.isBlank()) ||
                                    (categoryCode != null && !categoryCode.isBlank()) ||
                                    categoryId != null ||
                                    unitId != null;

        if (hasCatalogFilters) {
            matchingVariantIds = catalogClient.searchVariantIds(keyword, sku, productCode, productName, categoryCode, categoryId, unitId);
            if (matchingVariantIds == null || matchingVariantIds.isEmpty()) {
                return new PageResponse<>(
                        Collections.emptyList(),
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        0,
                        0,
                        true,
                        true
                );
            }
        }

        // 2. Resolve default warehouse
        final InventoryLocation defaultWarehouse;
        InventoryLocation tempWarehouse = null;
        if (branchId != null) {
            tempWarehouse = locationRepository
                    .findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                    .orElse(null);
        }
        if (tempWarehouse == null) {
            tempWarehouse = locationRepository
                    .findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                    .or(() -> locationRepository.findFirstByType(InventoryLocationType.WAREHOUSE))
                    .orElse(null);
        }
        defaultWarehouse = tempWarehouse;

        // 3. Query local InventoryBalances with filters
        final List<UUID> finalMatchingVariantIds = matchingVariantIds;
        Specification<InventoryBalance> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (hasCatalogFilters && finalMatchingVariantIds != null) {
                predicates.add(root.get("inventoryItem").get("referenceId").in(finalMatchingVariantIds));
            }

            // Always only display balances on locations where type = SHELF
            predicates.add(cb.equal(root.get("location").get("type"), InventoryLocationType.SHELF));

            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            } else if (defaultWarehouse != null) {
                // Only show shelves that belong to the default warehouse
                predicates.add(cb.equal(root.get("location").get("parentLocation").get("id"), defaultWarehouse.getId()));
            }

            if (branchId != null) {
                predicates.add(cb.equal(root.get("location").get("branchId"), branchId));
            }

            if (status != null && status != StockType.ALL) {
                if (status == StockType.LOW_STOCK) {
                    predicates.add(cb.greaterThan(root.get("quantityOnHand"), BigDecimal.ZERO));
                    predicates.add(cb.lessThanOrEqualTo(
                            root.get("quantityOnHand"),
                            cb.coalesce(root.get("inventoryItem").get("reorderPoint"), 5).as(BigDecimal.class)
                    ));
                } else if (status == StockType.OUT_OF_STOCK) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("quantityOnHand"), BigDecimal.ZERO));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<InventoryBalance> balancePage = repository.findAll(specification, pageable);
        if (balancePage.isEmpty()) {
            return new PageResponse<>(
                    Collections.emptyList(),
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
        Map<UUID, CatalogClient.VariantResponse> variantMap = variants.stream()
                .collect(Collectors.toMap(CatalogClient.VariantResponse::id, v -> v, (v1, v2) -> v1));

        List<UUID> productIds = variants.stream().map(CatalogClient.VariantResponse::productId).distinct().toList();
        List<CatalogClient.ProductResponse> products = catalogClient.getProductsBatch(productIds);
        Map<UUID, CatalogClient.ProductResponse> productMap = products.stream()
                .collect(Collectors.toMap(CatalogClient.ProductResponse::id, p -> p, (p1, p2) -> p1));

        List<UUID> unitIds = variants.stream()
                .map(CatalogClient.VariantResponse::unitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<CatalogClient.UnitResponse> units = catalogClient.getUnitsBatch(unitIds);
        Map<UUID, CatalogClient.UnitResponse> unitMap = units.stream()
                .collect(Collectors.toMap(CatalogClient.UnitResponse::id, u -> u, (u1, u2) -> u1));

        // 4. Map and Join
        List<InventoryBalanceViewResponse> mappedList = balancePage.getContent().stream()
                .map(b -> {
                    CatalogClient.VariantResponse v = variantMap.get(b.getInventoryItem().getReferenceId());
                    CatalogClient.ProductResponse p = (v != null) ? productMap.get(v.productId()) : null;
                    CatalogClient.CategoryResponse cat = (p != null) ? p.category() : null;
                    CatalogClient.UnitResponse u = (v != null) ? unitMap.get(v.unitId()) : null;
                    InventoryLocation loc = b.getLocation();

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
                            .unitId(u != null ? u.id() : (v != null ? v.unitId() : null))
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
    public List<InventoryBalanceWarehouseResponse> findWarehouseBalances(
            String sku,
            String productName
    ) {
        // 1. Get first location whose type is WAREHOUSE
        Optional<InventoryLocation> warehouseLocOpt =
                locationRepository.findFirstByType(InventoryLocationType.WAREHOUSE);
        if (warehouseLocOpt.isEmpty()) {
            return Collections.emptyList();
        }
        InventoryLocation warehouseLoc = warehouseLocOpt.get();

        // 2. Query matching variant IDs from Catalog Service if sku or productName is provided
        List<UUID> matchingVariantIds = null;
        if ((sku != null && !sku.isBlank()) || (productName != null && !productName.isBlank())) {
            matchingVariantIds = catalogClient.searchVariantIds(
                    null,
                    sku != null ? sku.trim() : null,
                    null,
                    productName != null ? productName.trim() : null,
                    null,
                    null,
                    null
            );
            if (matchingVariantIds == null || matchingVariantIds.isEmpty()) {
                return Collections.emptyList();
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
                .map(b -> new InventoryBalanceWarehouseResponse(
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

    @Override
    @Transactional
    public void bulkUpdateLocation() {
        InventoryLocation newLocation = locationRepository.findFirstByType(InventoryLocationType.SHELF)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí lưu trữ nào có loại SHELF", null));

        List<InventoryBalance> balancesToUpdate = repository.findAll();

        for (InventoryBalance balance : balancesToUpdate) {
            // Skip if already in the new location
            if (balance.getLocation().getId().equals(newLocation.getId())) {
                continue;
            }

            UUID itemId = balance.getInventoryItem().getId();
            Optional<InventoryBalance> existingBalanceOpt = repository.findByInventoryItemIdAndLocationId(itemId, newLocation.getId());

            if (existingBalanceOpt.isPresent()) {
                // If a balance already exists at the target location, merge the quantities and delete the old record
                InventoryBalance existingBalance = existingBalanceOpt.get();
                existingBalance.setQuantityOnHand(existingBalance.getQuantityOnHand().add(balance.getQuantityOnHand()));
                existingBalance.setQuantityReserved(existingBalance.getQuantityReserved().add(balance.getQuantityReserved()));
                repository.save(existingBalance);
                repository.delete(balance);
            } else {
                // Otherwise, safely change the location reference
                balance.setLocation(newLocation);
                repository.save(balance);
            }
        }
    }
}
