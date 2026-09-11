package org.hvtra.iventoryservice.stock.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.StockType;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryBalanceServiceImpl implements InventoryBalanceService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final InventoryBalanceRepository repository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceMapper mapper;
    private final CatalogClient catalogClient;

    private static class AggregatedBalance {
        UUID representativeBalanceId;
        InventoryItem inventoryItem;
        InventoryLocation location;
        BigDecimal quantityOnHand = BigDecimal.ZERO;
        BigDecimal quantityReserved = BigDecimal.ZERO;
        BigDecimal quantityBlocked = BigDecimal.ZERO;
        Instant updatedAt;
    }

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
            String code,
            String sku,
            String productCode,
            String name,
            String productName,
            String categoryCode,
            UUID categoryId,
            UUID unitId,
            UUID locationId,
            UUID branchId,
            StockType status,
            InventoryItemType itemType,
            Pageable pageable
    ) {
        String searchCode = (code != null && !code.isBlank()) ? code : sku;
        String searchName = (name != null && !name.isBlank()) ? name : productName;

        // 1. Resolve variant & ingredient IDs if catalog filters are active
        List<UUID> matchingVariantIds = null;
        List<UUID> matchingIngredientIds = null;
        boolean hasCatalogFilters = (keyword != null && !keyword.isBlank()) ||
                                    (searchCode != null && !searchCode.isBlank()) ||
                                    (productCode != null && !productCode.isBlank()) ||
                                    (searchName != null && !searchName.isBlank()) ||
                                    (categoryCode != null && !categoryCode.isBlank()) ||
                                    categoryId != null ||
                                    unitId != null;

        if (hasCatalogFilters) {
            boolean shouldSearchVariants = (itemType == null || itemType == InventoryItemType.PRODUCT_VARIANT);
            boolean shouldSearchIngredients = (itemType == null || itemType == InventoryItemType.INGREDIENT)
                    && (categoryCode == null || categoryCode.isBlank()) && categoryId == null && (productCode == null || productCode.isBlank());

            if (shouldSearchVariants) {
                matchingVariantIds = catalogClient.searchVariantIds(keyword, searchCode, productCode, searchName, categoryCode, categoryId, unitId);
            }
            if (shouldSearchIngredients) {
                matchingIngredientIds = catalogClient.searchIngredientIds(keyword, searchCode, searchName, unitId);
            }

            int foundCount = (matchingVariantIds != null ? matchingVariantIds.size() : 0) + (matchingIngredientIds != null ? matchingIngredientIds.size() : 0);
            if (foundCount == 0) {
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

        // 2. Query local InventoryBalances with filters
        final List<UUID> finalMatchingVariantIds = matchingVariantIds;
        final List<UUID> finalMatchingIngredientIds = matchingIngredientIds;
        Specification<InventoryBalance> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (itemType != null) {
                predicates.add(cb.equal(root.get("inventoryItem").get("itemType"), itemType));
            }

            if (hasCatalogFilters) {
                List<UUID> allRefs = new ArrayList<>();
                if (finalMatchingVariantIds != null) allRefs.addAll(finalMatchingVariantIds);
                if (finalMatchingIngredientIds != null) allRefs.addAll(finalMatchingIngredientIds);
                if (allRefs.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("inventoryItem").get("referenceId").in(allRefs));
            }

            if (locationId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("location").get("id"), locationId),
                        cb.equal(root.get("location").get("parentLocation").get("id"), locationId)
                ));
            } else if (branchId != null) {
                predicates.add(cb.equal(root.get("location").get("branchId"), branchId));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<InventoryBalance> allBalances = repository.findAll(specification);
        if (allBalances.isEmpty()) {
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

        // 3. Aggregate balances across batches by (inventoryItemId + locationId)
        Map<String, AggregatedBalance> aggregatedMap = new LinkedHashMap<>();
        for (InventoryBalance b : allBalances) {
            if (b.getInventoryItem() == null) continue;
            UUID itemId = b.getInventoryItem().getId();
            UUID locId = b.getLocation() != null ? b.getLocation().getId() : null;
            String key = itemId + "_" + locId;

            AggregatedBalance agg = aggregatedMap.computeIfAbsent(key, k -> {
                AggregatedBalance a = new AggregatedBalance();
                a.representativeBalanceId = b.getId();
                a.inventoryItem = b.getInventoryItem();
                a.location = b.getLocation();
                a.updatedAt = b.getUpdatedAt();
                return a;
            });

            if (b.getQuantityOnHand() != null) {
                agg.quantityOnHand = agg.quantityOnHand.add(b.getQuantityOnHand());
            }
            if (b.getQuantityReserved() != null) {
                agg.quantityReserved = agg.quantityReserved.add(b.getQuantityReserved());
            }
            if (b.getQuantityBlocked() != null) {
                agg.quantityBlocked = agg.quantityBlocked.add(b.getQuantityBlocked());
            }
            if (b.getUpdatedAt() != null && (agg.updatedAt == null || b.getUpdatedAt().isAfter(agg.updatedAt))) {
                agg.updatedAt = b.getUpdatedAt();
            }
        }

        // 4. Apply status filter (LOW_STOCK, OUT_OF_STOCK, ALL)
        List<AggregatedBalance> filteredList = aggregatedMap.values().stream()
                .filter(agg -> {
                    if (status == null || status == StockType.ALL) {
                        return true;
                    }
                    Integer reorderPoint = agg.inventoryItem.getReorderPoint() != null ? agg.inventoryItem.getReorderPoint() : 5;
                    BigDecimal reorderBd = BigDecimal.valueOf(reorderPoint);

                    if (status == StockType.LOW_STOCK) {
                        return agg.quantityOnHand.compareTo(BigDecimal.ZERO) > 0
                                && agg.quantityOnHand.compareTo(reorderBd) <= 0;
                    } else if (status == StockType.OUT_OF_STOCK) {
                        return agg.quantityOnHand.compareTo(BigDecimal.ZERO) <= 0;
                    }
                    return true;
                })
                .toList();

        // 5. In-memory pagination
        int totalElements = filteredList.size();
        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : 20;
        int pageNumber = pageable.getPageNumber();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);

        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<AggregatedBalance> pagedAggregated = filteredList.subList(fromIndex, toIndex);

        if (pagedAggregated.isEmpty()) {
            return new PageResponse<>(
                    Collections.emptyList(),
                    pageNumber,
                    pageSize,
                    totalElements,
                    totalPages,
                    pageNumber == 0,
                    pageNumber >= totalPages - 1
            );
        }

        // 6. Batch resolve catalog entities for mapped records in memory
        List<UUID> variantIds = pagedAggregated.stream()
                .filter(agg -> agg.inventoryItem.getItemType() == InventoryItemType.PRODUCT_VARIANT)
                .map(agg -> agg.inventoryItem.getReferenceId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UUID> ingredientIds = pagedAggregated.stream()
                .filter(agg -> agg.inventoryItem.getItemType() == InventoryItemType.INGREDIENT)
                .map(agg -> agg.inventoryItem.getReferenceId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<CatalogClient.VariantResponse> variants = !variantIds.isEmpty() ? catalogClient.getVariantsBatch(variantIds) : Collections.emptyList();
        Map<UUID, CatalogClient.VariantResponse> variantMap = variants != null
                ? variants.stream().collect(Collectors.toMap(CatalogClient.VariantResponse::id, v -> v, (v1, v2) -> v1))
                : Collections.emptyMap();

        List<UUID> productIds = variants != null
                ? variants.stream().map(CatalogClient.VariantResponse::productId).filter(Objects::nonNull).distinct().toList()
                : Collections.emptyList();
        List<CatalogClient.ProductResponse> products = !productIds.isEmpty() ? catalogClient.getProductsBatch(productIds) : Collections.emptyList();
        Map<UUID, CatalogClient.ProductResponse> productMap = products != null
                ? products.stream().collect(Collectors.toMap(CatalogClient.ProductResponse::id, p -> p, (p1, p2) -> p1))
                : Collections.emptyMap();

        List<UUID> unitIds = variants != null
                ? variants.stream().map(CatalogClient.VariantResponse::unitId).filter(Objects::nonNull).distinct().toList()
                : Collections.emptyList();
        List<CatalogClient.UnitResponse> units = !unitIds.isEmpty() ? catalogClient.getUnitsBatch(unitIds) : Collections.emptyList();
        Map<UUID, CatalogClient.UnitResponse> unitMap = units != null
                ? units.stream().collect(Collectors.toMap(CatalogClient.UnitResponse::id, u -> u, (u1, u2) -> u1))
                : Collections.emptyMap();

        List<CatalogClient.IngredientResponse> ingredients = !ingredientIds.isEmpty() ? catalogClient.getIngredientsBatch(ingredientIds) : Collections.emptyList();
        Map<UUID, CatalogClient.IngredientResponse> ingredientMap = ingredients != null
                ? ingredients.stream().collect(Collectors.toMap(CatalogClient.IngredientResponse::id, i -> i, (i1, i2) -> i1))
                : Collections.emptyMap();

        // 7. Map and construct final response
        List<InventoryBalanceViewResponse> mappedList = pagedAggregated.stream()
                .map(agg -> {
                    InventoryLocation loc = agg.location;
                    BigDecimal available = saleableAvailable(agg.quantityOnHand, agg.quantityReserved, agg.quantityBlocked);
                    InventoryItemType itemTypeVal = agg.inventoryItem.getItemType();

                    if (itemTypeVal == InventoryItemType.INGREDIENT) {
                        CatalogClient.IngredientResponse ing = ingredientMap.get(agg.inventoryItem.getReferenceId());
                        String ingCode = ing != null ? ing.ingredientCode() : agg.inventoryItem.getReferenceCode();
                        String ingName = agg.inventoryItem.getName() != null && !agg.inventoryItem.getName().isBlank()
                                ? agg.inventoryItem.getName()
                                : (ing != null ? ing.name() : (agg.inventoryItem.getReferenceCode() != null ? agg.inventoryItem.getReferenceCode() : "N/A"));
                        String ingImage = ing != null ? ing.imageUrl() : null;
                        UUID uId = ing != null ? ing.unitId() : agg.inventoryItem.getUnitId();
                        String uName = ing != null ? ing.unitName() : null;
                        String uSymbol = ing != null ? ing.unitSymbol() : null;

                        return InventoryBalanceViewResponse.builder()
                                .id(agg.representativeBalanceId)
                                .inventoryBalanceId(agg.representativeBalanceId)
                                .inventoryItemId(agg.inventoryItem.getId())
                                .referenceId(agg.inventoryItem.getReferenceId())
                                .itemType(itemTypeVal)
                                .code(ingCode)
                                .name(ingName)
                                .productId(null)
                                .productCode(null)
                                .productName(null)
                                .categoryId(null)
                                .categoryCode(null)
                                .categoryName(null)
                                .unitId(uId)
                                .unitName(uName)
                                .unitSymbol(uSymbol)
                                .locationId(loc != null ? loc.getId() : null)
                                .locationName(loc != null ? loc.getName() : null)
                                .branchId(loc != null ? loc.getBranchId() : null)
                                .imageUrl(ingImage)
                                .costPrice(null)
                                .sellingPrice(null)
                                .quantityOnHand(agg.quantityOnHand)
                                .quantityReserved(agg.quantityReserved)
                                .availableQuantity(available)
                                .reorderPoint(agg.inventoryItem.getReorderPoint())
                                .updatedAt(agg.updatedAt)
                                .build();
                    } else {
                        CatalogClient.VariantResponse v = variantMap.get(agg.inventoryItem.getReferenceId());
                        CatalogClient.ProductResponse p = (v != null && v.productId() != null) ? productMap.get(v.productId()) : null;
                        CatalogClient.CategoryResponse cat = (p != null) ? p.category() : null;
                        CatalogClient.UnitResponse u = (v != null && v.unitId() != null) ? unitMap.get(v.unitId()) : null;

                        String codeVal = v != null ? (v.sku() != null ? v.sku() : v.productCode()) : agg.inventoryItem.getReferenceCode();
                        String nameVal = agg.inventoryItem.getName() != null && !agg.inventoryItem.getName().isBlank()
                                ? agg.inventoryItem.getName()
                                : (v != null && v.variantName() != null ? v.variantName() : (p != null ? p.name() : (v != null ? v.productName() : "N/A")));

                        return InventoryBalanceViewResponse.builder()
                                .id(agg.representativeBalanceId)
                                .inventoryBalanceId(agg.representativeBalanceId)
                                .inventoryItemId(agg.inventoryItem.getId())
                                .referenceId(agg.inventoryItem.getReferenceId())
                                .itemType(itemTypeVal)
                                .code(codeVal)
                                .name(nameVal)
                                .productId(p != null ? p.id() : null)
                                .productCode(p != null ? p.productCode() : (v != null ? v.productCode() : null))
                                .productName(p != null ? p.name() : (v != null ? v.productName() : null))
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
                                .quantityOnHand(agg.quantityOnHand)
                                .quantityReserved(agg.quantityReserved)
                                .availableQuantity(available)
                                .reorderPoint(agg.inventoryItem.getReorderPoint())
                                .updatedAt(agg.updatedAt)
                                .build();
                    }
                })
                .toList();

        return new PageResponse<>(
                mappedList,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber >= totalPages - 1
        );
    }

    @Override
    public List<InventoryBalanceWarehouseResponse> findWarehouseBalances(
            String sku,
            String productName
    ) {
        // 1. Get first location whose type is WAREHOUSE
        Optional<InventoryLocation> warehouseLocOpt =
                locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
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

        // 3. Find balances for that location or its children
        final List<UUID> finalMatchingVariantIds = matchingVariantIds;
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.or(
                    cb.equal(root.get("location").get("id"), warehouseLoc.getId()),
                    cb.equal(root.get("location").get("parentLocation").get("id"), warehouseLoc.getId())
            ));
            predicates.add(cb.equal(root.get("location").get("status"), InventoryStatus.ACTIVE));
            predicates.add(cb.not(root.get("location").get("type").in(InventoryLocationSaleability.nonSaleableTypes())));
            if (finalMatchingVariantIds != null) {
                predicates.add(root.get("inventoryItem").get("referenceId").in(finalMatchingVariantIds));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<InventoryBalance> balances = repository.findAll(spec);

        Map<UUID, AggregatedBalance> aggregatedMap = new LinkedHashMap<>();
        for (InventoryBalance b : balances) {
            if (b.getInventoryItem() == null || !InventoryLocationSaleability.isSaleable(b.getLocation())) continue;
            UUID itemId = b.getInventoryItem().getId();

            AggregatedBalance agg = aggregatedMap.computeIfAbsent(itemId, ignored -> {
                AggregatedBalance a = new AggregatedBalance();
                a.representativeBalanceId = b.getId();
                a.inventoryItem = b.getInventoryItem();
                a.location = b.getLocation();
                return a;
            });
            if (b.getQuantityOnHand() != null) {
                agg.quantityOnHand = agg.quantityOnHand.add(b.getQuantityOnHand());
            }
            if (b.getQuantityReserved() != null) {
                agg.quantityReserved = agg.quantityReserved.add(b.getQuantityReserved());
            }
            if (b.getQuantityBlocked() != null) {
                agg.quantityBlocked = agg.quantityBlocked.add(b.getQuantityBlocked());
            }
        }

        return aggregatedMap.values().stream()
                .map(agg -> new InventoryBalanceWarehouseResponse(
                        agg.representativeBalanceId,
                        agg.location.getId(),
                        agg.inventoryItem.getId(),
                        agg.inventoryItem.getReferenceId(),
                        agg.inventoryItem.getItemType(),
                        agg.quantityOnHand,
                        agg.quantityReserved,
                        saleableAvailable(agg.quantityOnHand, agg.quantityReserved, agg.quantityBlocked),
                        agg.inventoryItem.getStatus()
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

    private BigDecimal saleableAvailable(
            BigDecimal quantityOnHand,
            BigDecimal quantityReserved,
            BigDecimal quantityBlocked) {
        BigDecimal available = (quantityOnHand != null ? quantityOnHand : ZERO)
                .subtract(quantityReserved != null ? quantityReserved : ZERO)
                .subtract(quantityBlocked != null ? quantityBlocked : ZERO);
        return available.compareTo(ZERO) < 0 ? ZERO : available;
    }
}
