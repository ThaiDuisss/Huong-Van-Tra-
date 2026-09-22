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
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderMaterialAllocation;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderSequence;
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
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CompleteProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.mapper.ProductionOrderMapper;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.production.repository.ProductionOrderSequenceRepository;
import org.hvtra.iventoryservice.production.service.ProductionOrderService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ProductionOrderSequenceRepository sequenceRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CatalogClient catalogClient;
    private final ProductionOrderMapper mapper;

    public ProductionOrderServiceImpl(
            ProductionOrderRepository productionOrderRepository,
            ProductionOrderSequenceRepository sequenceRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBatchRepository batchRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            CatalogClient catalogClient,
            ProductionOrderMapper mapper
    ) {
        this.productionOrderRepository = productionOrderRepository;
        this.sequenceRepository = sequenceRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.catalogClient = catalogClient;
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
                predicates.add(cb.equal(root.get("productVariantId"), productVariantId));
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

        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", request.locationId()));

        ProductionOrder order = new ProductionOrder();
        order.setOrderCode(generateOrderCode());
        order.setProductVariantId(request.productVariantId());
        order.setPlannedQuantity(request.plannedQuantity());
        order.setUnitId(variant.unitId());
        order.setLocationId(location.getId());
        order.setNote(request.note());
        order.setStatus(ProductionOrderStatus.DRAFT);

        List<UUID> ingredientIds = bomItems.stream()
                .filter(bi -> bi.ingredient() != null)
                .map(bi -> bi.ingredient().id())
                .distinct()
                .toList();
        Map<UUID, InventoryItem> inventoryItemByIngredientId = itemRepository
                .findByReferenceIdInAndItemType(ingredientIds, InventoryItemType.INGREDIENT).stream()
                .collect(Collectors.toMap(InventoryItem::getReferenceId, Function.identity()));

        for (CatalogClient.BomItemResponse bomItem : bomItems) {
            if (bomItem.ingredient() == null) {
                continue;
            }
            InventoryItem inventoryItem = inventoryItemByIngredientId.get(bomItem.ingredient().id());
            if (inventoryItem == null) {
                throw new ResourceNotFoundException("InventoryItem for ingredient", bomItem.ingredient().id());
            }

            BigDecimal wasteRate = bomItem.wasteRate() != null ? bomItem.wasteRate() : BigDecimal.ZERO;
            BigDecimal requiredQuantity = bomItem.quantity()
                    .multiply(request.plannedQuantity())
                    .multiply(BigDecimal.ONE.add(wasteRate));

            ProductionOrderLine line = new ProductionOrderLine();
            line.setInventoryItem(inventoryItem);
            line.setIngredientId(bomItem.ingredient().id());
            line.setIngredientName(bomItem.ingredient().name());
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

        for (ProductionOrderLine line : order.getLines()) {
            BigDecimal remaining = line.getRequiredQuantity().subtract(line.getReservedQuantity());
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<InventoryBatch> candidates = batchRepository.findFefoCandidates(
                    line.getInventoryItem().getId(), InventoryBatchStatus.AVAILABLE);

            for (InventoryBatch batch : candidates) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                // Nguyên liệu không quản lý tồn theo kho sản xuất — lấy theo FEFO bất kể batch đang nằm
                // ở kho nào. "Kho / vị trí sản xuất" của lệnh (order.getLocationId()) chỉ là nơi NHẬP
                // thành phẩm sau khi hoàn tất, không phải nơi XUẤT nguyên liệu.
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

                    balance.setQuantityReserved(balance.getQuantityReserved().add(take));
                    balanceRepository.save(balance);

                    ProductionOrderMaterialAllocation allocation = new ProductionOrderMaterialAllocation();
                    allocation.setInventoryBatch(batch);
                    allocation.setLocationId(balance.getLocation().getId());
                    allocation.setReservedQuantity(take);
                    allocation.setStatus(ReservationStatus.RESERVED);
                    line.addAllocation(allocation);

                    line.setReservedQuantity(line.getReservedQuantity().add(take));
                    remaining = remaining.subtract(take);
                }
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new OutOfStockException(
                        "Không đủ tồn kho nguyên liệu \"" + line.getIngredientName() + "\": còn thiếu " + remaining);
            }
        }

        order.setStatus(ProductionOrderStatus.APPROVED);
        order.setApprovedBy(getCurrentUserId());
        order.setApprovedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse release(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.APPROVED);

        InventoryLocation location = locationRepository.findById(order.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", order.getLocationId()));
        UUID branchId = location.getBranchId();

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(InventoryTransactionType.PRODUCTION_ISSUE);
        transaction.setReferenceType(InventoryReferenceType.PRODUCTION_ORDER);
        transaction.setReferenceId(order.getId().toString());
        transaction.setBranchId(branchId != null ? branchId : location.getId());

        for (ProductionOrderLine line : order.getLines()) {
            for (ProductionOrderMaterialAllocation allocation : line.getAllocations()) {
                if (allocation.getStatus() != ReservationStatus.RESERVED) {
                    continue;
                }
                InventoryBatch batch = allocation.getInventoryBatch();
                // Phải lấy đúng kho đã trừ tồn lúc approve() (allocation.getLocationId()) — KHÔNG phải
                // order.getLocationId() (kho nhập thành phẩm), vì nguyên liệu không quản lý theo kho đó.
                InventoryBalance balance = balanceRepository
                        .findForUpdateByBatchId(batch.getId(), allocation.getLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("InventoryBalance for batch", batch.getId()));

                BigDecimal before = balance.getQuantityOnHand();
                BigDecimal after = before.subtract(allocation.getReservedQuantity());
                balance.setQuantityOnHand(after);
                balance.setQuantityReserved(balance.getQuantityReserved().subtract(allocation.getReservedQuantity()));
                balanceRepository.save(balance);

                allocation.setConsumedQuantity(allocation.getReservedQuantity());
                allocation.setStatus(ReservationStatus.CONFIRMED);
                line.setConsumedQuantity(line.getConsumedQuantity().add(allocation.getReservedQuantity()));

                InventoryTransactionLine transLine = new InventoryTransactionLine();
                transLine.setInventoryItem(line.getInventoryItem());
                transLine.setLocation(balance.getLocation());
                transLine.setQuantity(allocation.getReservedQuantity());
                transLine.setBeforeQuantity(before);
                transLine.setAfterQuantity(after);

                InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
                transAlloc.setInventoryBatch(batch);
                transAlloc.setQuantity(allocation.getReservedQuantity());
                transAlloc.setBeforeBatchQuantity(before);
                transAlloc.setAfterBatchQuantity(after);
                transLine.addBatchAllocation(transAlloc);

                transaction.addLine(transLine);
            }
        }

        transactionRepository.save(transaction);

        order.setStatus(ProductionOrderStatus.RELEASED);
        order.setReleasedBy(getCurrentUserId());
        order.setReleasedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse start(UUID id) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.RELEASED);
        order.setStatus(ProductionOrderStatus.IN_PROGRESS);
        order.setStartedBy(getCurrentUserId());
        order.setStartedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse complete(UUID id, CompleteProductionOrderRequest request) {
        ProductionOrder order = getOrderWithDetails(id);
        requireStatus(order, ProductionOrderStatus.IN_PROGRESS);

        BigDecimal totalOutput = request.actualGoodQuantity().add(request.actualRejectedQuantity());
        if (totalOutput.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Tổng số lượng thành phẩm đạt và loại bỏ phải lớn hơn 0");
        }

        InventoryLocation location = locationRepository.findById(order.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", order.getLocationId()));

        InventoryItem outputItem = itemRepository
                .findByReferenceIdAndItemType(order.getProductVariantId(), InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem for product variant", order.getProductVariantId()));

        if (request.actualGoodQuantity().compareTo(BigDecimal.ZERO) > 0) {
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

            InventoryBalance balance = balanceRepository.findForUpdateByBatchId(finishedBatch.getId(), location.getId())
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

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = before.add(request.actualGoodQuantity());
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);

            UUID branchId = location.getBranchId();
            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setTransactionCode(generateTransactionCode());
            transaction.setType(InventoryTransactionType.PRODUCTION_RECEIPT);
            transaction.setReferenceType(InventoryReferenceType.PRODUCTION_ORDER);
            transaction.setReferenceId(order.getId().toString());
            transaction.setBranchId(branchId != null ? branchId : location.getId());

            InventoryTransactionLine transLine = new InventoryTransactionLine();
            transLine.setInventoryItem(outputItem);
            transLine.setLocation(location);
            transLine.setQuantity(request.actualGoodQuantity());
            transLine.setBeforeQuantity(before);
            transLine.setAfterQuantity(after);

            InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
            transAlloc.setInventoryBatch(finishedBatch);
            transAlloc.setQuantity(request.actualGoodQuantity());
            transAlloc.setBeforeBatchQuantity(before);
            transAlloc.setAfterBatchQuantity(after);
            transLine.addBatchAllocation(transAlloc);

            transaction.addLine(transLine);
            transactionRepository.save(transaction);

            order.setFinishedBatchId(finishedBatch.getId());
        }

        order.setActualGoodQuantity(request.actualGoodQuantity());
        order.setActualRejectedQuantity(request.actualRejectedQuantity());
        if (request.note() != null && !request.note().isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + request.note());
        }
        order.setStatus(ProductionOrderStatus.COMPLETED);
        order.setCompletedBy(getCurrentUserId());
        order.setCompletedAt(Instant.now());
        return mapper.toResponse(productionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public ProductionOrderResponse cancel(UUID id, CancelProductionOrderRequest request) {
        ProductionOrder order = getOrderWithDetails(id);
        if (order.getStatus() != ProductionOrderStatus.DRAFT
                && order.getStatus() != ProductionOrderStatus.PENDING_APPROVAL
                && order.getStatus() != ProductionOrderStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể hủy lệnh sản xuất ở trạng thái DRAFT, PENDING_APPROVAL hoặc APPROVED");
        }

        if (order.getStatus() == ProductionOrderStatus.APPROVED) {
            for (ProductionOrderLine line : order.getLines()) {
                for (ProductionOrderMaterialAllocation allocation : line.getAllocations()) {
                    if (allocation.getStatus() != ReservationStatus.RESERVED) {
                        continue;
                    }
                    // Đúng kho đã trừ tồn lúc approve() (allocation.getLocationId()), không phải kho sản xuất.
                    InventoryBalance balance = balanceRepository
                            .findForUpdateByBatchId(allocation.getInventoryBatch().getId(), allocation.getLocationId())
                            .orElse(null);
                    if (balance != null) {
                        balance.setQuantityReserved(balance.getQuantityReserved().subtract(allocation.getReservedQuantity()));
                        balanceRepository.save(balance);
                    }
                    allocation.setStatus(ReservationStatus.RELEASED);
                }
            }
        }

        order.setStatus(ProductionOrderStatus.CANCELLED);
        order.setCancelledBy(getCurrentUserId());
        order.setCancelledAt(Instant.now());
        order.setCancelReason(request.reason());
        return mapper.toResponse(productionOrderRepository.save(order));
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
        ProductionOrderSequence sequence = sequenceRepository.findBySequenceKeyWithLock(SEQUENCE_KEY)
                .orElseGet(() -> {
                    ProductionOrderSequence newSeq = new ProductionOrderSequence();
                    newSeq.setSequenceKey(SEQUENCE_KEY);
                    newSeq.setCurrentValue(0L);
                    return sequenceRepository.saveAndFlush(newSeq);
                });
        long nextVal = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextVal);
        sequenceRepository.saveAndFlush(sequence);
        return String.format("%s-%05d", SEQUENCE_KEY, nextVal);
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
