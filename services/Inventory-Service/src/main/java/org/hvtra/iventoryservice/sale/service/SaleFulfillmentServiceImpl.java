package org.hvtra.iventoryservice.sale.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservationBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.service.ShelfReplenishmentService;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.hvtra.iventoryservice.sale.dto.FulfillSaleRequest;
import org.hvtra.iventoryservice.sale.dto.FulfillSaleResponse;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SaleFulfillmentServiceImpl implements SaleFulfillmentService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Instant NO_EXPIRY = Instant.parse("9999-12-31T00:00:00Z");
    private static final List<InventoryReplenishmentAlertType> MATERIAL_ALERT_TYPES = List.of(
            InventoryReplenishmentAlertType.LOW_STOCK,
            InventoryReplenishmentAlertType.OUT_OF_STOCK,
            InventoryReplenishmentAlertType.REORDER_REQUIRED);

    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReplenishmentAlertRepository alertRepository;
    private final ShelfReplenishmentService replenishmentService;

    public SaleFulfillmentServiceImpl(
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            InventoryReservationRepository reservationRepository,
            InventoryReplenishmentAlertRepository alertRepository,
            ShelfReplenishmentService replenishmentService) {
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.alertRepository = alertRepository;
        this.replenishmentService = replenishmentService;
    }

    @Override
    @Transactional
    public FulfillSaleResponse fulfill(FulfillSaleRequest request) {
        String transactionCode = "SALE-" + request.orderId();
        if (transactionRepository.existsByTransactionCode(transactionCode)) {
            return new FulfillSaleResponse(request.orderId(), true, null, List.of(), List.of());
        }

        Map<UUID, BigDecimal> quantityByVariant = new LinkedHashMap<>();
        request.lines().forEach(line -> quantityByVariant.merge(line.productVariantId(), line.quantity(), BigDecimal::add));
        Map<UUID, InventoryItem> itemByVariant = resolveProductItems(quantityByVariant.keySet());

        InventoryLocation warehouse = resolveWarehouse(request.branchId());
        Set<UUID> shelfIds = new LinkedHashSet<>();
        locationRepository.findByParentLocation_IdAndTypeAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                        warehouse.getId(), InventoryLocationType.SHELF, InventoryStatus.ACTIVE)
                .forEach(shelf -> shelfIds.add(shelf.getId()));

        // 1. Kệ thiếu bao nhiêu, kho có đủ bù không.
        Map<UUID, BigDecimal> shortageByVariant = new LinkedHashMap<>();
        for (var entry : quantityByVariant.entrySet()) {
            InventoryItem item = itemByVariant.get(entry.getKey());
            BigDecimal onShelf = sumAvailable(shelfBalances(item, shelfIds));
            BigDecimal shortage = entry.getValue().subtract(onShelf);
            if (shortage.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal inWarehouse = sumAvailable(balanceRepository.findFefoAvailableForUpdate(
                    item.getId(), warehouse.getId(), InventoryStatus.ACTIVE, LocalDate.now()));
            if (inWarehouse.compareTo(shortage) < 0) {
                throw new OutOfStockException("Không đủ hàng cho " + item.getName()
                        + ": cần " + plain(entry.getValue())
                        + ", trên kệ " + plain(onShelf)
                        + ", trong kho " + plain(inWarehouse));
            }
            shortageByVariant.put(entry.getKey(), shortage);
        }

        // 2. Xuất kho lên kệ ngay cho phần thiếu; manager duyệt phiếu sau.
        ShelfReplenishmentResponse slip = replenishmentService.autoIssueForOrder(
                request.orderId(), request.branchId(), shortageByVariant);
        if (slip != null && slip.shelfId() != null) {
            shelfIds.add(slip.shelfId());
        }

        // 3. Trừ thành phẩm trên kệ theo FEFO.
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(transactionCode);
        transaction.setType(InventoryTransactionType.SALE_OUT);
        transaction.setReferenceType(InventoryReferenceType.ORDER);
        transaction.setReferenceId(request.orderId().toString());
        transaction.setBranchId(warehouse.getBranchId() != null ? warehouse.getBranchId() : warehouse.getId());
        for (var entry : quantityByVariant.entrySet()) {
            deductFromShelves(itemByVariant.get(entry.getKey()), entry.getValue(), shelfIds, transaction);
        }
        transactionRepository.save(transaction);

        // 4. Giữ chỗ nguyên liệu cho việc sản xuất bù + cảnh báo khi chạm ngưỡng.
        List<FulfillSaleResponse.MaterialResult> materialResults =
                reserveMaterialsForRestock(request, warehouse);

        // 5. Tồn còn lại để nơi gọi so với ngưỡng sản xuất.
        List<UUID> itemIds = itemByVariant.values().stream().map(InventoryItem::getId).toList();
        List<UUID> locationIds = new ArrayList<>(shelfIds);
        locationIds.add(warehouse.getId());
        Map<String, BigDecimal> remaining = new HashMap<>();
        for (Object[] row : balanceRepository.sumAvailableByItemsAndLocations(itemIds, locationIds)) {
            remaining.put(row[0] + ":" + row[1], (BigDecimal) row[2]);
        }
        List<FulfillSaleResponse.LineResult> lineResults = new ArrayList<>();
        for (var entry : quantityByVariant.entrySet()) {
            InventoryItem item = itemByVariant.get(entry.getKey());
            BigDecimal shelfRemaining = shelfIds.stream()
                    .map(shelfId -> remaining.getOrDefault(item.getId() + ":" + shelfId, ZERO))
                    .reduce(ZERO, BigDecimal::add);
            lineResults.add(new FulfillSaleResponse.LineResult(
                    entry.getKey(),
                    entry.getValue(),
                    shortageByVariant.getOrDefault(entry.getKey(), ZERO),
                    shelfRemaining,
                    remaining.getOrDefault(item.getId() + ":" + warehouse.getId(), ZERO)));
        }
        return new FulfillSaleResponse(
                request.orderId(), false, slip != null ? slip.documentCode() : null, lineResults, materialResults);
    }

    // ---------------------------------------------------------------- finished goods

    private List<InventoryBalance> shelfBalances(InventoryItem item, Set<UUID> shelfIds) {
        if (shelfIds.isEmpty()) {
            return List.of();
        }
        return balanceRepository.findFefoAvailableForUpdateInLocations(
                item.getId(), shelfIds, InventoryStatus.ACTIVE, LocalDate.now());
    }

    private void deductFromShelves(InventoryItem item, BigDecimal quantity, Set<UUID> shelfIds,
            InventoryTransaction transaction) {
        BigDecimal remaining = quantity;
        for (InventoryBalance balance : shelfBalances(item, shelfIds)) {
            if (remaining.compareTo(ZERO) <= 0) {
                break;
            }
            BigDecimal take = balance.getAvailableQuantity().min(remaining);
            if (take.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = before.subtract(take);
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);

            InventoryTransactionLine line = new InventoryTransactionLine();
            line.setInventoryItem(item);
            line.setLocation(balance.getLocation());
            line.setQuantity(take.negate());
            line.setBeforeQuantity(before);
            line.setAfterQuantity(after);
            InventoryTransactionBatchAllocation allocation = new InventoryTransactionBatchAllocation();
            allocation.setInventoryBatch(balance.getInventoryBatch());
            allocation.setQuantity(take.negate());
            allocation.setBeforeBatchQuantity(before);
            allocation.setAfterBatchQuantity(after);
            line.addBatchAllocation(allocation);
            transaction.addLine(line);

            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(ZERO) > 0) {
            throw new OutOfStockException("Không đủ hàng trên kệ cho " + item.getName()
                    + ": còn thiếu " + plain(remaining));
        }
    }

    // ---------------------------------------------------------------- materials

    /**
     * Bán bao nhiêu thì phải sản xuất bù bấy nhiêu, nên giữ chỗ nguyên liệu theo BOM ngay lúc bán để biết còn đủ
     * nguyên liệu hay không. Thiếu nguyên liệu không làm hỏng đơn bán: chỉ giữ phần đang có và tạo cảnh báo.
     */
    private List<FulfillSaleResponse.MaterialResult> reserveMaterialsForRestock(
            FulfillSaleRequest request, InventoryLocation warehouse) {
        if (request.materials() == null || request.materials().isEmpty()) {
            return List.of();
        }
        Map<String, FulfillSaleRequest.MaterialLine> merged = new LinkedHashMap<>();
        for (FulfillSaleRequest.MaterialLine line : request.materials()) {
            merged.merge(line.productVariantId() + ":" + line.ingredientId(), line,
                    (left, right) -> new FulfillSaleRequest.MaterialLine(
                            left.productVariantId(), left.ingredientId(), left.quantity().add(right.quantity())));
        }

        List<FulfillSaleResponse.MaterialResult> results = new ArrayList<>();
        for (FulfillSaleRequest.MaterialLine line : merged.values()) {
            InventoryItem material = itemRepository
                    .findByReferenceIdAndItemType(line.ingredientId(), InventoryItemType.INGREDIENT)
                    .filter(item -> item.getStatus() == InventoryStatus.ACTIVE)
                    .orElse(null);
            if (material == null) {
                log.warn("Sale {}: ingredient {} has no active inventory item, skip material reservation",
                        request.orderId(), line.ingredientId());
                results.add(new FulfillSaleResponse.MaterialResult(line.productVariantId(), line.ingredientId(),
                        line.quantity(), ZERO, line.quantity(), ZERO, false));
                continue;
            }

            List<InventoryBalance> candidates = balanceRepository.findFefoAvailableForUpdate(
                    material.getId(), warehouse.getId(), InventoryStatus.ACTIVE, LocalDate.now());
            BigDecimal availableBefore = sumAvailable(candidates);
            BigDecimal toReserve = line.quantity().min(availableBefore);

            if (toReserve.compareTo(ZERO) > 0) {
                InventoryReservation reservation = new InventoryReservation();
                reservation.setOrderId(request.orderId());
                reservation.setProductVariantId(line.productVariantId());
                reservation.setIngredientId(line.ingredientId());
                reservation.setInventoryItem(material);
                reservation.setLocation(warehouse);
                reservation.setQuantity(toReserve);
                reservation.setStatus(ReservationStatus.RESERVED);
                reservation.setReservationType(ReservationType.MATERIAL);
                reservation.setProcessingAttempt(request.processingAttempt());
                reservation.setExpiresAt(NO_EXPIRY);

                BigDecimal remaining = toReserve;
                for (InventoryBalance balance : candidates) {
                    if (remaining.compareTo(ZERO) <= 0) {
                        break;
                    }
                    BigDecimal take = balance.getAvailableQuantity().min(remaining);
                    if (take.compareTo(ZERO) <= 0) {
                        continue;
                    }
                    InventoryReservationBatchAllocation allocation = new InventoryReservationBatchAllocation();
                    allocation.setInventoryBatch(balance.getInventoryBatch());
                    allocation.setQuantity(take);
                    allocation.setStatus(ReservationStatus.RESERVED);
                    reservation.addBatchAllocation(allocation);
                    balance.setQuantityReserved(balance.getQuantityReserved().add(take));
                    balanceRepository.save(balance);
                    remaining = remaining.subtract(take);
                }
                reservationRepository.save(reservation);
            }

            BigDecimal shortage = line.quantity().subtract(toReserve);
            BigDecimal availableAfter = availableBefore.subtract(toReserve);
            boolean alertRaised = raiseMaterialAlertIfNeeded(material, warehouse, availableAfter, shortage);
            results.add(new FulfillSaleResponse.MaterialResult(line.productVariantId(), line.ingredientId(),
                    line.quantity(), toReserve, shortage, availableAfter, alertRaised));
        }
        return results;
    }

    /** Cảnh báo nhập nguyên liệu khi không đủ để giữ chỗ hoặc tồn khả dụng chạm ngưỡng reorderPoint. */
    private boolean raiseMaterialAlertIfNeeded(InventoryItem material, InventoryLocation warehouse,
            BigDecimal availableAfter, BigDecimal shortage) {
        BigDecimal reorderPoint = material.getReorderPoint() == null
                ? null : BigDecimal.valueOf(material.getReorderPoint());
        boolean short_ = shortage.compareTo(ZERO) > 0;
        boolean belowThreshold = reorderPoint != null && availableAfter.compareTo(reorderPoint) <= 0;
        if (!short_ && !belowThreshold) {
            return false;
        }
        if (alertRepository.existsOpenAlert(material.getId(), warehouse.getId(), MATERIAL_ALERT_TYPES)) {
            return false;
        }
        BigDecimal toThreshold = reorderPoint == null ? ZERO : reorderPoint.subtract(availableAfter).max(ZERO);
        InventoryReplenishmentAlert alert = new InventoryReplenishmentAlert();
        alert.setInventoryItem(material);
        alert.setLocation(warehouse);
        alert.setAlertType(short_ || availableAfter.compareTo(ZERO) <= 0
                ? InventoryReplenishmentAlertType.OUT_OF_STOCK
                : InventoryReplenishmentAlertType.REORDER_REQUIRED);
        alert.setAvailableQuantitySnapshot(availableAfter);
        alert.setReorderPointSnapshot(reorderPoint == null ? ZERO : reorderPoint);
        alert.setSuggestedQuantity(shortage.max(ZERO).add(toThreshold));
        alert.setOpenedAt(Instant.now());
        alertRepository.save(alert);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private Map<UUID, InventoryItem> resolveProductItems(Set<UUID> variantIds) {
        Map<UUID, InventoryItem> itemByVariant = new HashMap<>();
        for (InventoryItem item : itemRepository.findByReferenceIdInAndItemType(variantIds, InventoryItemType.PRODUCT_VARIANT)) {
            if (item.getStatus() == InventoryStatus.ACTIVE) {
                itemByVariant.putIfAbsent(item.getReferenceId(), item);
            }
        }
        for (UUID variantId : variantIds) {
            if (!itemByVariant.containsKey(variantId)) {
                throw new BadRequestException("Mặt hàng " + variantId + " chưa được khai báo trong kho");
            }
        }
        return itemByVariant;
    }

    private InventoryLocation resolveWarehouse(UUID branchId) {
        if (branchId != null) {
            var byBranch = locationRepository.findFirstByBranchIdAndTypeAndStatus(
                    branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
            if (byBranch.isPresent()) {
                return byBranch.get();
            }
        }
        return locationRepository
                .findFirstByTypeAndStatusOrderByCreatedAtAsc(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Chưa có kho nào đang hoạt động"));
    }

    private BigDecimal sumAvailable(List<InventoryBalance> balances) {
        return balances.stream().map(InventoryBalance::getAvailableQuantity).reduce(ZERO, BigDecimal::add);
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
