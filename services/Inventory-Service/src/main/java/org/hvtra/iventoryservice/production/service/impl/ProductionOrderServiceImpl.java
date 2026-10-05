package org.hvtra.iventoryservice.production.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
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
import org.hvtra.iventoryservice.shared.sequence.BusinessSequenceService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            InventoryReservationService reservationService
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
        this.mapper = mapper;
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
        return PageResponse.from(productionOrderRepository.findAll(spec, pageable).map(mapper::toSummaryResponse));
    }

    @Override
    public ProductionOrderResponse findById(UUID id) {
        return mapper.toResponse(getOrderWithDetails(id));
    }

    @Override
    @Transactional
    public ProductionOrderResponse create(CreateProductionOrderRequest request) {
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

            // Check if stock is sufficient
            List<InventoryBalance> balances = balanceRepository.findActiveWarehouseBalancesByInventoryItemIds(List.of(ingredientItem.getId()));
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

        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse submit(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.DRAFT);
        if (order.getLines().isEmpty()) {
            throw new BadRequestException("Lệnh sản xuất chưa có dòng nguyên liệu nào");
        }
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        order.setSubmittedBy(getCurrentUserId());
        order.setSubmittedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse approve(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.PENDING_APPROVAL);

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
                    if (available.compareTo(BigDecimal.ZERO) <= 0) {
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
                    allocation.setConsumedQuantity(take);
                    allocation.setStatus(ReservationStatus.CONFIRMED);
                    line.addAllocation(allocation);
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
        order.setApprovedBy(getCurrentUserId());
        order.setApprovedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }




    @Override
    @Transactional
    public ProductionOrderResponse reject(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.PENDING_APPROVAL);

        order.setStatus(ProductionOrderStatus.REJECTED);
        order.setRejectedBy(getCurrentUserId());
        order.setRejectedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse cancel(UUID id, CancelProductionOrderRequest request) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.DRAFT);

        // Check creator == currentUser
        UUID currentUserId = getCurrentUserId();
        if (!order.getCreatedBy().equals(currentUserId)) {
            throw new BadRequestException("Chỉ có thể hủy lệnh sản xuất do chính mình tạo");
        }

        order.setStatus(ProductionOrderStatus.CANCELLED);
        order.setCancelledBy(getCurrentUserId());
        order.setCancelledAt(Instant.now());
        order.setCancelReason(request.reason());
        return mapper.toResponse(productionOrderRepository.save(order));
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
