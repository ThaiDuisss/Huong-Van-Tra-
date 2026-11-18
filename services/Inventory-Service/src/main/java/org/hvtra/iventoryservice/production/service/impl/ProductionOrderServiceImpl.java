package org.hvtra.iventoryservice.production.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderBatchAllocation;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.mapper.ProductionOrderMapper;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.production.service.ProductionOrderService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProductionOrderServiceImpl implements ProductionOrderService {

    private static final String SEQUENCE_KEY = "LSX";

    private final ProductionOrderRepository productionOrderRepository;
    private final BusinessSequenceService sequenceService;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CatalogClient catalogClient;
    private final ProductionOrderMapper mapper;
    private final InventoryReservationService reservationService;
    private final SupplyLeadTimeService supplyLeadTimeService;
    private final IdentityClient identityClient;

    public ProductionOrderServiceImpl(
            ProductionOrderRepository productionOrderRepository,
            BusinessSequenceService sequenceService,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBatchRepository batchRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            CatalogClient catalogClient,
            ProductionOrderMapper mapper,
            InventoryReservationService reservationService,
            SupplyLeadTimeService supplyLeadTimeService,
            @Autowired(required = false) IdentityClient identityClient
    ) {
        this.productionOrderRepository = productionOrderRepository;
        this.sequenceService = sequenceService;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.catalogClient = catalogClient;
        this.reservationService = reservationService;
        this.supplyLeadTimeService = supplyLeadTimeService;
        this.mapper = mapper;
        this.identityClient = identityClient;
    }

    private String fetchUserName(UUID userId) {
        if (userId == null || identityClient == null) {
            return null;
        }
        try {
            List<IdentityClient.UserBriefResponse> users = identityClient.getUsersBatch(List.of(userId.toString()));
            if (users != null && !users.isEmpty() && users.getFirst() != null) {
                IdentityClient.UserBriefResponse u = users.getFirst();
                return (u.fullName() != null && !u.fullName().isBlank()) ? u.fullName() : u.username();
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy tên user từ Identity-Service: {}", ex.getMessage());
        }
        return null;
    }

    private Map<UUID, String> fetchUserNames(Collection<UUID> userIds) {
        Map<UUID, String> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty() || identityClient == null) {
            return map;
        }
        List<String> validIds = userIds.stream().filter(Objects::nonNull).map(UUID::toString).distinct().toList();
        if (validIds.isEmpty()) {
            return map;
        }
        try {
            List<IdentityClient.UserBriefResponse> users = identityClient.getUsersBatch(validIds);
            if (users != null) {
                for (IdentityClient.UserBriefResponse u : users) {
                    if (u != null && u.keycloakSubject() != null) {
                        try {
                            UUID uid = UUID.fromString(u.keycloakSubject());
                            String name = (u.fullName() != null && !u.fullName().isBlank()) ? u.fullName() : u.username();
                            map.put(uid, name);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy tên user từ Identity-Service: {}", ex.getMessage());
        }
        return map;
    }

    @Override
    public PageResponse<ProductionOrderResponse> findAll(
            ProductionOrderStatus status, UUID productVariantId, String search, Pageable pageable) {
        Specification<ProductionOrder> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productVariantId != null) {
                predicates.add(cb.equal(root.get("productVariant").get("referenceId"), productVariantId));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("orderCode")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ProductionOrder> page = productionOrderRepository.findAll(spec, pageable);
        Set<UUID> userIds = page.getContent().stream()
                .map(ProductionOrder::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> userNames = fetchUserNames(userIds);
        return PageResponse.from(page.map(o -> mapper.toSummaryResponse(o, userNames.get(o.getCreatedBy()))));
    }

    @Override
    public ProductionOrderResponse findById(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        String createByName = fetchUserName(order.getCreatedBy());
        return mapper.toResponse(order, createByName);
    }

    @Override
    @Transactional
    public ProductionOrderResponse create(CreateProductionOrderRequest request) {
        return create(request, true);
    }

    @Override
    @Transactional
    public ProductionOrderResponse createDraft(CreateProductionOrderRequest request) {
        return create(request, false);
    }

    private ProductionOrderResponse create(CreateProductionOrderRequest request, boolean requireMaterialStock) {
        CatalogClient.VariantResponse variant = catalogClient.getVariant(request.productVariantId());
        if (variant == null) {
            throw new ResourceNotFoundException("Product variant", request.productVariantId());
        }

        List<CatalogClient.BomItemResponse> bomItems = catalogClient.getBomItems(request.productVariantId());
        if (bomItems.isEmpty()) {
            throw new BadRequestException(
                    "Sản phẩm này chưa có định mức nguyên liệu (BOM) — vui lòng cập nhật BOM trước khi tạo lệnh sản xuất");
        }

        // Check BOM status = APPROVED
        if (!isBomApproved(request.productVariantId())) {
            throw new BadRequestException("BOM của sản phẩm phải ở trạng thái APPROVED trước khi tạo lệnh sản xuất");
        }

        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", request.locationId()));

        // Get InventoryItem for product variant
        InventoryItem variantItem = itemRepository.findByReferenceIdAndItemType(
                request.productVariantId(), InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem for variant", request.productVariantId()));

        // Collect ingredient IDs and check stock availability
        List<UUID> ingredientIds = bomItems.stream()
                .filter(bi -> bi.ingredient() != null)
                .map(bi -> bi.ingredient().id())
                .distinct()
                .toList();
        Map<UUID, InventoryItem> inventoryItemByIngredientId = itemRepository
                .findByReferenceIdInAndItemType(ingredientIds, InventoryItemType.INGREDIENT).stream()
                .collect(Collectors.toMap(InventoryItem::getReferenceId, Function.identity()));

        // Auto check: Verify ingredient stock >= required quantity for all lines
        for (CatalogClient.BomItemResponse bomItem : bomItems) {
            if (!requireMaterialStock || bomItem.ingredient() == null) {
                continue;
            }
            InventoryItem ingredientItem = inventoryItemByIngredientId.get(bomItem.ingredient().id());
            if (ingredientItem == null) {
                throw new ResourceNotFoundException("InventoryItem for ingredient", bomItem.ingredient().id());
            }

            BigDecimal wasteRate = bomItem.wasteRate() != null ? bomItem.wasteRate() : BigDecimal.ZERO;
            BigDecimal wasteMultiplier = BigDecimal.ONE.add(
                    wasteRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal requiredQuantity = bomItem.quantity()
                    .multiply(request.plannedQuantity())
                    .multiply(wasteMultiplier);

            // Check if stock is sufficient (chỉ tính batch AVAILABLE, chưa hết hạn, tại kho WAREHOUSE)
            List<InventoryBalance> balances = balanceRepository.findActiveWarehouseBalancesByInventoryItemIds(
                    List.of(ingredientItem.getId()), LocalDate.now());
            BigDecimal availableStock = balances.stream()
                    .map(b -> b.getQuantityOnHand().subtract(b.getQuantityReserved()).subtract(b.getQuantityBlocked()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (availableStock.compareTo(requiredQuantity) < 0) {
                throw new OutOfStockException(
                        "Không đủ tồn kho nguyên liệu \"" + ingredientItem.getReferenceId() + "\": cần " + requiredQuantity + " nhưng chỉ có " + availableStock);
            }
        }

        ProductionOrder order = new ProductionOrder();
        order.setOrderCode(generateOrderCode());
        order.setProductVariant(variantItem);
        order.setPlannedQuantity(request.plannedQuantity());
        order.setUnitId(variant.unitId());
        order.setLocationId(location.getId());
        order.setNote(request.note());
        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setExpectedCompletionDate(Instant.now().plus(1, ChronoUnit.DAYS));
        UUID currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            order.setCreatedBy(currentUserId);
        }

        for (CatalogClient.BomItemResponse bomItem : bomItems) {
            if (bomItem.ingredient() == null) {
                continue;
            }
            InventoryItem ingredientItem = inventoryItemByIngredientId.get(bomItem.ingredient().id());
            if (ingredientItem == null) {
                throw new ResourceNotFoundException("InventoryItem for ingredient", bomItem.ingredient().id());
            }

            BigDecimal wasteRate = bomItem.wasteRate() != null ? bomItem.wasteRate() : BigDecimal.ZERO;
            BigDecimal wasteMultiplier = BigDecimal.ONE.add(
                    wasteRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal requiredQuantity = bomItem.quantity()
                    .multiply(request.plannedQuantity())
                    .multiply(wasteMultiplier);

            ProductionOrderLine line = new ProductionOrderLine();
            line.setIngredient(ingredientItem);
            line.setRequiredQuantity(requiredQuantity);
            line.setUnitId(bomItem.unit() != null ? bomItem.unit().id() : null);
            line.setWasteRate(wasteRate);
            order.addLine(line);
        }

        ProductionOrder saved = productionOrderRepository.save(order);
        String createByName = fetchUserName(saved.getCreatedBy());
        return mapper.toResponse(saved, createByName);
    }

    @Override
    @Transactional
    public ProductionOrderResponse submit(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.DRAFT);
        if (order.getLines().isEmpty()) {
            throw new BadRequestException("Lệnh sản xuất chưa có dòng nguyên liệu nào");
        }
        order.setSubmittedBy(getCurrentUserId());
        order.setSubmittedAt(Instant.now());

        // Quản lý hoặc Admin: không cần duyệt, tự động phê duyệt và xuất kho/nhập kho ngay
        if (isManagerOrAdmin()) {
            return executeApprove(order);
        }

        // Nhân viên kho: giữ chỗ nguyên liệu (FEFO) và chuyển sang trạng thái PENDING_APPROVAL chờ Quản lý/Admin duyệt
        reserveMaterials(order);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        ProductionOrder saved = productionOrderRepository.save(order);
        String createByName = fetchUserName(saved.getCreatedBy());
        return mapper.toResponse(saved, createByName);
    }

    @Override
    @Transactional
    public ProductionOrderResponse approve(UUID id) {
        if (!isManagerOrAdmin()) {
            throw new AccessDeniedException("Chỉ Quản lý hoặc Admin mới có quyền duyệt lệnh sản xuất");
        }
        ProductionOrder order = getOrderWithDetails(id);
        if (order.getStatus() != ProductionOrderStatus.PENDING_APPROVAL && order.getStatus() != ProductionOrderStatus.DRAFT) {
            throw new BadRequestException(
                    "Lệnh sản xuất phải ở trạng thái DRAFT hoặc PENDING_APPROVAL (hiện tại: " + order.getStatus() + ")");
        }
        return executeApprove(order);
    }

    private void reserveMaterials(ProductionOrder order) {
        for (ProductionOrderLine line : order.getLines()) {
            BigDecimal remaining = line.getRequiredQuantity();
            if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            reservationService.releasePlannedMaterials(
                    order.getProductVariant().getReferenceId(), line.getIngredient().getId(), remaining);

            List<InventoryBatch> candidates = batchRepository.findFefoCandidates(
                    line.getIngredient().getId(), InventoryBatchStatus.AVAILABLE);

            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (InventoryBatch batch : candidates) {
                List<InventoryBalance> balances = balanceRepository.findForUpdateByBatchId(batch.getId());
                for (InventoryBalance balance : balances) {
                    BigDecimal available = balance.getAvailableQuantity();
                    if (available != null && available.compareTo(BigDecimal.ZERO) > 0) {
                        totalAvailable = totalAvailable.add(available);
                    }
                }
            }

            if (totalAvailable.compareTo(remaining) < 0) {
                throw new OutOfStockException(
                        "Không đủ tồn kho nguyên liệu \"" + line.getIngredient().getReferenceId() + "\": cần " + remaining + " nhưng chỉ có " + totalAvailable);
            }

            BigDecimal lineReserved = BigDecimal.ZERO;
            for (InventoryBatch batch : candidates) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                List<InventoryBalance> balances = balanceRepository.findForUpdateByBatchId(batch.getId());
                for (InventoryBalance balance : balances) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    BigDecimal available = balance.getAvailableQuantity();
                    if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal take = available.min(remaining);

                    balance.setQuantityReserved(balance.getQuantityReserved().add(take));
                    balanceRepository.save(balance);

                    ProductionOrderBatchAllocation allocation = new ProductionOrderBatchAllocation();
                    allocation.setInventoryBatch(batch);
                    allocation.setLocationId(balance.getLocation().getId());
                    allocation.setReservedQuantity(take);
                    allocation.setConsumedQuantity(BigDecimal.ZERO);
                    allocation.setStatus(ReservationStatus.RESERVED);
                    line.addAllocation(allocation);

                    lineReserved = lineReserved.add(take);
                    remaining = remaining.subtract(take);
                }
            }
            line.setReservedQuantity(lineReserved);
        }
    }

    private void releaseReservedMaterials(ProductionOrder order) {
        for (ProductionOrderLine line : order.getLines()) {
            if (line.getAllocations() == null || line.getAllocations().isEmpty()) {
                continue;
            }
            for (ProductionOrderBatchAllocation allocation : line.getAllocations()) {
                if (allocation.getStatus() != ReservationStatus.RESERVED) {
                    continue;
                }
                BigDecimal reserved = allocation.getReservedQuantity();
                if (reserved == null || reserved.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                UUID locId = allocation.getLocationId();
                Optional<InventoryBalance> balanceOpt = locId != null
                        ? balanceRepository.findForUpdateByBatchId(allocation.getInventoryBatch().getId(), locId)
                        : balanceRepository.findForUpdateByBatchId(allocation.getInventoryBatch().getId()).stream().findFirst();

                balanceOpt.ifPresent(balance -> {
                    BigDecimal newReserved = balance.getQuantityReserved().subtract(reserved);
                    balance.setQuantityReserved(newReserved.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newReserved);
                    balanceRepository.save(balance);
                });
                allocation.setStatus(ReservationStatus.RELEASED);
            }
            line.setReservedQuantity(BigDecimal.ZERO);
        }
    }

    private ProductionOrderResponse executeApprove(ProductionOrder order) {
        InventoryLocation location = locationRepository.findById(order.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", order.getLocationId()));
        UUID branchId = location.getBranchId();

        InventoryTransaction ingredientTransaction = new InventoryTransaction();
        ingredientTransaction.setTransactionCode(generateTransactionCode());
        ingredientTransaction.setType(InventoryTransactionType.PRODUCTION_ISSUE);
        ingredientTransaction.setReferenceType(InventoryReferenceType.PRODUCTION_ORDER);
        ingredientTransaction.setReferenceId(order.getId().toString());
        ingredientTransaction.setBranchId(branchId != null ? branchId : location.getId());

        // Deduct ingredients from stock using FEFO batch allocation
        for (ProductionOrderLine line : order.getLines()) {
            BigDecimal remaining = line.getRequiredQuantity();

            // Nguyên liệu đã được giữ chỗ lúc bán hàng (để sản xuất bù) thì trả lại cho chính lệnh này dùng,
            // nếu không phần giữ chỗ đó sẽ khóa nguyên liệu mãi.
            reservationService.releasePlannedMaterials(
                    order.getProductVariant().getReferenceId(), line.getIngredient().getId(), remaining);

            List<ProductionOrderBatchAllocation> existingAllocations = line.getAllocations() != null
                    ? line.getAllocations().stream()
                            .filter(a -> a.getStatus() == ReservationStatus.RESERVED)
                            .toList()
                    : List.of();

            if (!existingAllocations.isEmpty()) {
                // Trường hợp đã giữ chỗ lúc gửi duyệt: tiêu thụ các lô đã giữ chỗ
                for (ProductionOrderBatchAllocation allocation : existingAllocations) {
                    BigDecimal take = allocation.getReservedQuantity();
                    UUID locId = allocation.getLocationId();
                    InventoryBalance balance = (locId != null
                            ? balanceRepository.findForUpdateByBatchId(allocation.getInventoryBatch().getId(), locId)
                            : balanceRepository.findForUpdateByBatchId(allocation.getInventoryBatch().getId()).stream().findFirst())
                            .orElseThrow(() -> new ResourceNotFoundException("InventoryBalance for batch", allocation.getInventoryBatch().getId()));

                    BigDecimal beforeQuantity = balance.getQuantityOnHand();
                    BigDecimal afterQuantity = beforeQuantity.subtract(take);
                    balance.setQuantityOnHand(afterQuantity);
                    BigDecimal newReserved = balance.getQuantityReserved().subtract(take);
                    balance.setQuantityReserved(newReserved.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newReserved);
                    balanceRepository.save(balance);

                    allocation.setConsumedQuantity(take);
                    allocation.setStatus(ReservationStatus.CONFIRMED);
                    line.setConsumedQuantity(line.getConsumedQuantity().add(take));

                    InventoryTransactionLine transLine = new InventoryTransactionLine();
                    transLine.setInventoryItem(line.getIngredient());
                    transLine.setLocation(balance.getLocation());
                    transLine.setQuantity(take);
                    transLine.setBeforeQuantity(beforeQuantity);
                    transLine.setAfterQuantity(afterQuantity);

                    InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
                    transAlloc.setInventoryBatch(allocation.getInventoryBatch());
                    transAlloc.setQuantity(take);
                    transAlloc.setBeforeBatchQuantity(beforeQuantity);
                    transAlloc.setAfterBatchQuantity(afterQuantity);
                    transLine.addBatchAllocation(transAlloc);

                    ingredientTransaction.addLine(transLine);
                }
            } else {
                // Trường hợp chưa giữ chỗ trước (ví dụ Quản lý duyệt ngay): tìm lô FEFO và trừ trực tiếp
                List<InventoryBatch> candidates = batchRepository.findFefoCandidates(
                        line.getIngredient().getId(), InventoryBatchStatus.AVAILABLE);

                for (InventoryBatch batch : candidates) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    List<InventoryBalance> balances = balanceRepository.findForUpdateByBatchId(batch.getId());
                    for (InventoryBalance balance : balances) {
                        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                            break;
                        }
                        BigDecimal available = balance.getAvailableQuantity();
                        if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        BigDecimal take = available.min(remaining);

                        BigDecimal beforeQuantity = balance.getQuantityOnHand();
                        BigDecimal afterQuantity = beforeQuantity.subtract(take);
                        balance.setQuantityOnHand(afterQuantity);
                        balanceRepository.save(balance);

                        ProductionOrderBatchAllocation allocation = new ProductionOrderBatchAllocation();
                        allocation.setInventoryBatch(batch);
                        allocation.setLocationId(balance.getLocation().getId());
                        allocation.setReservedQuantity(take);
                        allocation.setConsumedQuantity(take);
                        allocation.setStatus(ReservationStatus.CONFIRMED);
                        line.addAllocation(allocation);
                        line.setReservedQuantity(line.getReservedQuantity().add(take));
                        line.setConsumedQuantity(line.getConsumedQuantity().add(take));

                        // Create transaction line for ingredient deduction
                        InventoryTransactionLine transLine = new InventoryTransactionLine();
                        transLine.setInventoryItem(line.getIngredient());
                        transLine.setLocation(balance.getLocation());
                        transLine.setQuantity(take);
                        transLine.setBeforeQuantity(beforeQuantity);
                        transLine.setAfterQuantity(afterQuantity);

                        InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
                        transAlloc.setInventoryBatch(batch);
                        transAlloc.setQuantity(take);
                        transAlloc.setBeforeBatchQuantity(beforeQuantity);
                        transAlloc.setAfterBatchQuantity(afterQuantity);
                        transLine.addBatchAllocation(transAlloc);

                        ingredientTransaction.addLine(transLine);
                        remaining = remaining.subtract(take);
                    }
                }

                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    throw new OutOfStockException(
                            "Không đủ tồn kho nguyên liệu \"" + line.getIngredient().getReferenceId() + "\": còn thiếu " + remaining);
                }
            }
        }

        transactionRepository.save(ingredientTransaction);

        // Add finished product to stock (plannedQuantity)
        InventoryItem outputItem = itemRepository
                .findByReferenceIdAndItemType(order.getProductVariant().getReferenceId(), InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem for product variant", order.getProductVariant().getReferenceId()));

        String batchCode = order.getOrderCode() + "-TP";
        InventoryBatch finishedBatch = batchRepository.findByInventoryItemIdAndBatchCode(outputItem.getId(), batchCode)
                .orElseGet(() -> {
                    InventoryBatch newBatch = new InventoryBatch();
                    newBatch.setInventoryItem(outputItem);
                    newBatch.setBatchCode(batchCode);
                    newBatch.setManufactureDate(java.time.LocalDate.now());
                    newBatch.setStatus(InventoryBatchStatus.AVAILABLE);
                    newBatch.setSourceType(InventoryReferenceType.PRODUCTION_ORDER);
                    newBatch.setSourceReferenceId(order.getId().toString());
                    newBatch.setReceivedAt(Instant.now());
                    return batchRepository.save(newBatch);
                });

        InventoryBalance finishedBalance = balanceRepository.findForUpdateByBatchId(finishedBatch.getId(), location.getId())
                .orElseGet(() -> {
                    InventoryBalance newBalance = new InventoryBalance();
                    newBalance.setInventoryItem(outputItem);
                    newBalance.setInventoryBatch(finishedBatch);
                    newBalance.setLocation(location);
                    newBalance.setQuantityOnHand(BigDecimal.ZERO);
                    newBalance.setQuantityReserved(BigDecimal.ZERO);
                    newBalance.setQuantityBlocked(BigDecimal.ZERO);
                    return newBalance;
                });

        BigDecimal beforeProductQuantity = finishedBalance.getQuantityOnHand();
        BigDecimal afterProductQuantity = beforeProductQuantity.add(order.getPlannedQuantity());
        finishedBalance.setQuantityOnHand(afterProductQuantity);
        balanceRepository.save(finishedBalance);

        // Create transaction for finished product receipt
        InventoryTransaction productTransaction = new InventoryTransaction();
        productTransaction.setTransactionCode(generateTransactionCode());
        productTransaction.setType(InventoryTransactionType.PRODUCTION_RECEIPT);
        productTransaction.setReferenceType(InventoryReferenceType.PRODUCTION_ORDER);
        productTransaction.setReferenceId(order.getId().toString());
        productTransaction.setBranchId(branchId != null ? branchId : location.getId());

        InventoryTransactionLine productTransLine = new InventoryTransactionLine();
        productTransLine.setInventoryItem(outputItem);
        productTransLine.setLocation(location);
        productTransLine.setQuantity(order.getPlannedQuantity());
        productTransLine.setBeforeQuantity(beforeProductQuantity);
        productTransLine.setAfterQuantity(afterProductQuantity);

        InventoryTransactionBatchAllocation productTransAlloc = new InventoryTransactionBatchAllocation();
        productTransAlloc.setInventoryBatch(finishedBatch);
        productTransAlloc.setQuantity(order.getPlannedQuantity());
        productTransAlloc.setBeforeBatchQuantity(beforeProductQuantity);
        productTransAlloc.setAfterBatchQuantity(afterProductQuantity);
        productTransLine.addBatchAllocation(productTransAlloc);

        productTransaction.addLine(productTransLine);
        transactionRepository.save(productTransaction);

        order.setStatus(ProductionOrderStatus.APPROVED);
        UUID userId = getCurrentUserId();
        if (order.getApprovedBy() == null) {
            order.setApprovedBy(userId);
        }
        if (order.getApprovedAt() == null) {
            order.setApprovedAt(Instant.now());
        }
        ProductionOrder approved = productionOrderRepository.save(order);
        supplyLeadTimeService.onProductionCompleted(approved);
        String createByName = fetchUserName(approved.getCreatedBy());
        return mapper.toResponse(approved, createByName);
    }

    @Override
    @Transactional
    public ProductionOrderResponse reject(UUID id) {
        if (!isManagerOrAdmin()) {
            throw new AccessDeniedException("Chỉ Quản lý hoặc Admin mới có quyền từ chối lệnh sản xuất");
        }
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.PENDING_APPROVAL);

        releaseReservedMaterials(order);
        order.setStatus(ProductionOrderStatus.REJECTED);
        order.setRejectedBy(getCurrentUserId());
        order.setRejectedAt(Instant.now());
        ProductionOrder saved = productionOrderRepository.save(order);
        String createByName = fetchUserName(saved.getCreatedBy());
        return mapper.toResponse(saved, createByName);
    }

    @Override
    @Transactional
    public ProductionOrderResponse cancel(UUID id, CancelProductionOrderRequest request) {
        ProductionOrder order = getOrderWithDetails(id);
        if (order.getStatus() != ProductionOrderStatus.DRAFT && order.getStatus() != ProductionOrderStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Lệnh sản xuất phải ở trạng thái DRAFT hoặc PENDING_APPROVAL (hiện tại: " + order.getStatus() + ")");
        }

        // Check creator/submitter == currentUser (chỉ người tạo mới được quyền hủy)
        UUID currentUserId = getCurrentUserId();
        boolean isCreator = currentUserId != null && (currentUserId.equals(order.getCreatedBy()) || currentUserId.equals(order.getSubmittedBy()));
        if (!isCreator) {
            throw new BadRequestException("Chỉ người tạo mới có quyền hủy lệnh sản xuất");
        }

        releaseReservedMaterials(order);
        order.setStatus(ProductionOrderStatus.CANCELLED);
        order.setCancelledBy(currentUserId);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(request.reason());
        ProductionOrder saved = productionOrderRepository.save(order);
        String createByName = fetchUserName(saved.getCreatedBy());
        return mapper.toResponse(saved, createByName);
    }

    private boolean isManagerOrAdmin() {
        try {
            return SecurityUtils.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBomApproved(UUID productVariantId) {
        List<CatalogClient.BomItemResponse> bomItems = catalogClient.getBomItems(productVariantId);
        return bomItems != null && !bomItems.isEmpty();
    }

    /**
     * Chỉ JOIN FETCH "lines" (1 bag) — không fetch kèm "lines.allocations" vì Hibernate không cho phép
     * fetch nhiều collection dạng List cùng lúc (MultipleBagFetchException). "allocations" của từng dòng
     * vẫn lazy-load bình thường khi thật sự cần (approve/release/cancel) — nhờ @BatchSize trên
     * {@code ProductionOrderLine.allocations}, Hibernate tự gộp các lần lazy-load đó của nhiều dòng
     * thành 1 câu "IN (...)" thay vì N query riêng lẻ, nên vẫn tránh được N+1 mà không cần tự tay
     * nạp/gán lại collection (collection có orphanRemoval=true không được phép thay bằng List mới).
     */
    private ProductionOrder getOrderWithDetails(UUID id) {
        return productionOrderRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionOrder", id));
    }

    private void requireStatus(ProductionOrder order, ProductionOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new BadRequestException(
                    "Lệnh sản xuất phải ở trạng thái " + expected + " (hiện tại: " + order.getStatus() + ")");
        }
    }

    private String generateOrderCode() {
        return sequenceService.nextCode(SEQUENCE_KEY, 5);
    }

    private String generateTransactionCode() {
        return "TX-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InternalUserPrincipal principal) {
            String userId = principal.userId();
            if (userId != null && !userId.isBlank()) {
                try {
                    return UUID.fromString(userId);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }
}
