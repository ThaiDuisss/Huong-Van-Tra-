package org.hvtra.iventoryservice.reservation.service;

import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialLineRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservationBatchAllocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryReservationServiceImpl(
            InventoryReservationRepository reservationRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository) {
        this.reservationRepository = reservationRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public InventoryReservationResponse apply(ApplyInventoryReservationRequest request) {
        BigDecimal targetQuantity = normalizeQuantity(request.targetQuantity());
        InventoryItem item = activeProductVariantItem(request.productVariantId());
        InventoryLocation location = resolveApplyLocation(request);
        InventoryReservation reservation = reservationRepository
                .findByOrderIdAndProductVariantIdAndLocationId(
                        request.orderId(),
                        request.productVariantId(),
                        location.getId())
                .orElseGet(() -> newReservation(request.orderId(), request.productVariantId(), item, location));

        BigDecimal oldQuantity = reservation.getStatus() == ReservationStatus.RESERVED
                ? normalizeQuantity(reservation.getQuantity())
                : BigDecimal.ZERO;
        BigDecimal delta = targetQuantity.subtract(oldQuantity);

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            reserve(item.getId(), location.getId(), delta, request.productVariantId());
            reservation.setQuantity(targetQuantity);
            reservation.setStatus(ReservationStatus.RESERVED);
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            release(item.getId(), location.getId(), delta.abs());
            reservation.setQuantity(targetQuantity);
            reservation.setStatus(targetQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? ReservationStatus.RELEASED
                    : ReservationStatus.RESERVED);
        }

        if (delta.compareTo(BigDecimal.ZERO) == 0 && targetQuantity.compareTo(BigDecimal.ZERO) > 0) {
            reservation.setStatus(ReservationStatus.RESERVED);
        }
        reservation.setExpiresAt(request.expiresAt());

        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public InventoryReservationResponse releaseItem(ReleaseInventoryReservationItemRequest request) {
        InventoryLocation location = request.locationId() == null ? null : activeLocation(request.locationId(), null);
        List<InventoryReservation> reservations = location == null
                ? reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED)
                        .stream()
                        .filter(reservation -> reservation.getProductVariantId().equals(request.productVariantId()))
                        .toList()
                : reservationRepository.findByOrderIdAndProductVariantIdAndLocationId(
                        request.orderId(),
                        request.productVariantId(),
                        location.getId())
                        .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                        .stream()
                        .toList();

        if (reservations.isEmpty()) {
            return new InventoryReservationResponse(
                    null,
                    request.orderId(),
                    request.productVariantId(),
                    null,
                    request.locationId(),
                    BigDecimal.ZERO,
                    ReservationStatus.RELEASED,
                    null);
        }

        reservations.forEach(this::releaseReservation);
        return toResponse(reservationRepository.save(reservations.get(0)));
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> releaseOrder(ReleaseInventoryReservationOrderRequest request) {
        return reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED)
                .stream()
                .map(reservation -> {
                    releaseReservation(reservation);
                    return toResponse(reservationRepository.save(reservation));
                })
                .toList();
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> confirmOrder(ConfirmInventoryReservationOrderRequest request) {
        // Chỉ xác nhận (trừ tồn) thành phẩm đã giữ chỗ. Nguyên liệu giữ để sản xuất bù sau khi bán
        // (reserveMaterialsForRestock) phải còn RESERVED cho tới khi lệnh sản xuất được duyệt.
        List<InventoryReservation> reservations = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                request.orderId(), ReservationType.PRODUCT_VARIANT, ReservationStatus.RESERVED);
        if (reservations.isEmpty()) {
            List<InventoryReservation> confirmed = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                    request.orderId(), ReservationType.PRODUCT_VARIANT, ReservationStatus.CONFIRMED);
            if (!confirmed.isEmpty()) {
                return confirmed.stream().map(this::toResponse).toList();
            }
            List<InventoryReservation> restockMaterials = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                    request.orderId(), ReservationType.MATERIAL, ReservationStatus.RESERVED);
            if (!restockMaterials.isEmpty()) {
                // Đơn bán tại quầy: thành phẩm đã trừ thẳng qua fulfillSale, không có gì để xác nhận thêm.
                return List.of();
            }
            throw new BadRequestException("No active reservation found for order " + request.orderId());
        }

        return reservations.stream()
                .map(reservation -> {
                    BigDecimal quantity = normalizeQuantity(reservation.getQuantity());
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        int updated = balanceRepository.confirmReserved(
                                reservation.getInventoryItem().getId(),
                                reservation.getLocation().getId(),
                                quantity,
                                InventoryStatus.ACTIVE,
                                InventoryLocationSaleability.nonSaleableTypes());
                        if (updated == 0) {
                            throw new OutOfStockException(
                                    "Reserved stock cannot be confirmed for productVariantId "
                                            + reservation.getProductVariantId());
                        }
                    }
                    reservation.setStatus(ReservationStatus.CONFIRMED);
                    return toResponse(reservationRepository.save(reservation));
                })
                .toList();
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> reserveMaterials(ReserveMaterialOrderRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("Material reservation lines are required");
        }
        List<InventoryReservation> alreadyReserved = reservationRepository
                .findByOrderIdAndReservationTypeAndStatus(
                        request.orderId(),
                        ReservationType.MATERIAL,
                        ReservationStatus.RESERVED);
        if (!alreadyReserved.isEmpty()) {
            if (alreadyReserved.stream().anyMatch(reservation -> reservation.getProcessingAttempt() != request.processingAttempt())) {
                throw new BadRequestException("Another material reservation attempt is still active");
            }
            return alreadyReserved.stream().map(this::toResponse).toList();
        }
        List<InventoryReservation> alreadyConsumed = reservationRepository
                .findByOrderIdAndReservationTypeAndStatus(request.orderId(), ReservationType.MATERIAL, ReservationStatus.CONFIRMED);
        if (!alreadyConsumed.isEmpty()) {
            return alreadyConsumed.stream().map(this::toResponse).toList();
        }

        Map<MaterialKey, MaterialRequirement> requirements = new LinkedHashMap<>();
        for (ReserveMaterialLineRequest line : request.lines()) {
            BigDecimal requiredQuantity = normalizeQuantity(line.quantity());
            if (requiredQuantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            InventoryItem item = line.inventoryItemId() == null
                    ? activeMaterialItemByIngredient(line.ingredientId())
                    : activeMaterialItem(line.inventoryItemId());
            InventoryLocation location = line.locationId() == null
                    ? resolveLocation(null, request.branchId())
                    : activeSaleableLocation(line.locationId(), request.branchId());
            MaterialKey key = new MaterialKey(item.getId(), location.getId());
            requirements.merge(key, new MaterialRequirement(line, item, location, requiredQuantity),
                    (existing, next) -> new MaterialRequirement(existing.line(), item, location,
                            existing.quantity().add(next.quantity())));
        }

        List<PendingMaterialReservation> pendingReservations = new ArrayList<>();
        for (MaterialRequirement requirement : requirements.values()) {
            List<InventoryBalance> candidates = balanceRepository.findFefoAvailableForUpdate(
                    requirement.item().getId(),
                    requirement.location().getId(),
                    InventoryStatus.ACTIVE,
                    LocalDate.now());
            pendingReservations.add(allocateMaterialReservation(
                    request.orderId(),
                    request.processingAttempt(),
                    request.expiresAt(),
                    requirement.line(),
                    requirement.item(),
                    requirement.location(),
                    candidates,
                    requirement.quantity()));
        }

        pendingReservations.forEach(PendingMaterialReservation::applyToBalances);
        return reservationRepository.saveAll(
                        pendingReservations.stream()
                                .map(PendingMaterialReservation::reservation)
                                .toList())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> consumeMaterials(ReleaseInventoryReservationOrderRequest request) {
        return consumeMaterials(request.orderId(), InventoryTransactionType.SALE_OUT, InventoryReferenceType.ORDER,
                request.orderId().toString(), "SALE-" + request.orderId());
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> consumeMaterials(UUID reservationKey, InventoryTransactionType type,
            InventoryReferenceType referenceType, String referenceId, String transactionCode) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                reservationKey,
                ReservationType.MATERIAL,
                ReservationStatus.RESERVED);
        if (reservations.isEmpty()) {
            return reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                            reservationKey,
                            ReservationType.MATERIAL,
                            ReservationStatus.CONFIRMED)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(transactionCode);
        transaction.setType(type);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        InventoryLocation firstLocation = reservations.getFirst().getLocation();
        transaction.setBranchId(firstLocation.getBranchId() == null ? firstLocation.getId() : firstLocation.getBranchId());
        reservations.forEach(reservation -> {
            reservation.getBatchAllocations().forEach(allocation -> {
                InventoryBalance balance = balanceRepository.findForUpdateByBatchId(
                                allocation.getInventoryBatch().getId(),
                                reservation.getLocation().getId())
                        .orElseThrow(() -> new BadRequestException("Reserved batch balance not found"));
                BigDecimal quantity = normalizeQuantity(allocation.getQuantity());
                if (balance.getQuantityReserved().compareTo(quantity) < 0
                        || balance.getQuantityOnHand().subtract(balance.getQuantityBlocked()).compareTo(quantity) < 0) {
                    throw new BadRequestException("Reserved material cannot be consumed");
                }
                balance.setQuantityReserved(balance.getQuantityReserved().subtract(quantity));
                balance.setQuantityOnHand(balance.getQuantityOnHand().subtract(quantity));
                InventoryTransactionLine movementLine = new InventoryTransactionLine();
                movementLine.setInventoryItem(reservation.getInventoryItem());
                movementLine.setLocation(reservation.getLocation());
                movementLine.setQuantity(quantity);
                movementLine.setBeforeQuantity(balance.getQuantityOnHand().add(quantity));
                movementLine.setAfterQuantity(balance.getQuantityOnHand());
                InventoryTransactionBatchAllocation movementAllocation = new InventoryTransactionBatchAllocation();
                movementAllocation.setInventoryBatch(allocation.getInventoryBatch());
                movementAllocation.setQuantity(quantity);
                movementAllocation.setBeforeBatchQuantity(movementLine.getBeforeQuantity());
                movementAllocation.setAfterBatchQuantity(movementLine.getAfterQuantity());
                movementLine.addBatchAllocation(movementAllocation);
                transaction.addLine(movementLine);
                allocation.setStatus(ReservationStatus.CONFIRMED);
            });
            reservation.setStatus(ReservationStatus.CONFIRMED);
        });
        transactionRepository.save(transaction);
        return reservationRepository.saveAll(reservations).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public List<InventoryReservationResponse> releaseMaterials(ReleaseInventoryReservationOrderRequest request) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                request.orderId(),
                ReservationType.MATERIAL,
                ReservationStatus.RESERVED);
        if (reservations.isEmpty()) {
            List<InventoryReservation> consumed = reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                    request.orderId(), ReservationType.MATERIAL, ReservationStatus.CONFIRMED);
            if (!consumed.isEmpty()) {
                return consumed.stream().map(this::toResponse).toList();
            }
            return reservationRepository.findByOrderIdAndReservationTypeAndStatus(
                            request.orderId(), ReservationType.MATERIAL, ReservationStatus.RELEASED)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        reservations.forEach(this::releaseMaterialReservation);
        return reservationRepository.saveAll(reservations).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public BigDecimal releasePlannedMaterials(UUID productVariantId, UUID inventoryItemId, BigDecimal quantity) {
        BigDecimal target = normalizeQuantity(quantity);
        BigDecimal released = BigDecimal.ZERO;
        if (productVariantId == null || inventoryItemId == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return released;
        }
        List<InventoryReservation> planned = reservationRepository
                .findByProductVariantIdAndInventoryItem_IdAndReservationTypeAndStatusOrderByCreatedAtAsc(
                        productVariantId, inventoryItemId, ReservationType.MATERIAL, ReservationStatus.RESERVED);
        for (InventoryReservation reservation : planned) {
            if (released.compareTo(target) >= 0) {
                break;
            }
            BigDecimal reservedQuantity = normalizeQuantity(reservation.getQuantity());
            releaseMaterialReservation(reservation);
            reservationRepository.save(reservation);
            released = released.add(reservedQuantity);
        }
        return released;
    }

    @Override
    @Transactional
    public int expireReservations() {
        List<InventoryReservation> reservations = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED,
                Instant.now()).stream()
                .filter(reservation -> reservation.getReservationType() != ReservationType.MATERIAL)
                .toList();
        reservations.forEach(reservation -> {
            releaseReservation(reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
        });
        return reservations.size();
    }

    private InventoryReservation newReservation(
            UUID orderId,
            UUID productVariantId,
            InventoryItem item,
            InventoryLocation location) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderId(orderId);
        reservation.setProductVariantId(productVariantId);
        reservation.setInventoryItem(item);
        reservation.setLocation(location);
        reservation.setReservationType(ReservationType.PRODUCT_VARIANT);
        reservation.setQuantity(BigDecimal.ZERO);
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }

    private void reserve(UUID itemId, UUID locationId, BigDecimal quantity, UUID productVariantId) {
        int updated = balanceRepository.reserveAvailable(
                itemId,
                locationId,
                quantity,
                InventoryStatus.ACTIVE,
                InventoryLocationSaleability.nonSaleableTypes());
        if (updated == 0) {
            throw new OutOfStockException("OUT_OF_STOCK productVariantId=" + productVariantId);
        }
    }

    private void releaseReservation(InventoryReservation reservation) {
        BigDecimal quantity = normalizeQuantity(reservation.getQuantity());
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            release(reservation.getInventoryItem().getId(), reservation.getLocation().getId(), quantity);
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setQuantity(BigDecimal.ZERO);
    }

    private void release(UUID itemId, UUID locationId, BigDecimal quantity) {
        int updated = balanceRepository.releaseReserved(itemId, locationId, quantity);
        if (updated == 0) {
            throw new BadRequestException("Reserved stock cannot be released");
        }
    }

    private InventoryItem activeProductVariantItem(UUID productVariantId) {
        InventoryItem item = itemRepository
                .findByReferenceIdAndItemType(productVariantId, InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item for product variant", productVariantId));
        if (item.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory item is inactive: " + item.getId());
        }
        return item;
    }

    private InventoryItem activeMaterialItem(UUID inventoryItemId) {
        InventoryItem item = itemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", inventoryItemId));
        if (item.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory item is inactive: " + inventoryItemId);
        }
        if (item.getItemType() != InventoryItemType.INGREDIENT) {
            throw new BadRequestException("Inventory item is not an ingredient: " + inventoryItemId);
        }
        return item;
    }

    private InventoryItem activeMaterialItemByIngredient(UUID ingredientId) {
        InventoryItem item = itemRepository.findByReferenceIdAndItemType(ingredientId, InventoryItemType.INGREDIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item for ingredient", ingredientId));
        return activeMaterialItem(item.getId());
    }

    private InventoryLocation resolveApplyLocation(ApplyInventoryReservationRequest request) {
        if (request.locationId() != null) {
            return activeSaleableLocation(request.locationId(), request.branchId());
        }

        List<InventoryReservation> existingReservations =
                reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED)
                        .stream()
                        .filter(reservation -> reservation.getProductVariantId().equals(request.productVariantId()))
                        .toList();
        if (existingReservations.size() == 1) {
            InventoryLocation location = existingReservations.get(0).getLocation();
            validateSaleableLocation(location);
            return location;
        }
        if (existingReservations.size() > 1) {
            throw new BadRequestException(
                    "Multiple active reservations found; locationId is required for productVariantId "
                            + request.productVariantId());
        }

        return resolveLocation(null, request.branchId());
    }

    private InventoryLocation resolveLocation(UUID locationId, UUID branchId) {
        if (locationId != null) {
            return activeSaleableLocation(locationId, branchId);
        }
        return branchId == null
                ? locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                        .orElseThrow(() -> new BadRequestException("No active WAREHOUSE location found"))
                : locationRepository.findFirstByBranchIdAndTypeAndStatus(
                        branchId,
                        InventoryLocationType.WAREHOUSE,
                        InventoryStatus.ACTIVE)
                        .orElseThrow(() -> new BadRequestException("No active WAREHOUSE location found for branch " + branchId));
    }

    private InventoryLocation activeLocation(UUID locationId, UUID branchId) {
        InventoryLocation location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory location", locationId));
        if (location.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory location is inactive: " + locationId);
        }
        if (branchId != null && !branchId.equals(location.getBranchId())) {
            throw new BadRequestException("Inventory location " + locationId + " does not belong to branch " + branchId);
        }
        return location;
    }

    private InventoryLocation activeSaleableLocation(UUID locationId, UUID branchId) {
        InventoryLocation location = activeLocation(locationId, branchId);
        validateSaleableLocation(location);
        return location;
    }

    private void validateSaleableLocation(InventoryLocation location) {
        if (!InventoryLocationSaleability.isSaleable(location)) {
            throw new BadRequestException("Inventory location is not saleable");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("targetQuantity cannot be negative");
        }
        return quantity;
    }

    private InventoryReservationResponse toResponse(InventoryReservation reservation) {
        return new InventoryReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getProductVariantId(),
                reservation.getInventoryItem().getId(),
                reservation.getLocation().getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt());
    }

    private PendingMaterialReservation allocateMaterialReservation(
            UUID orderId,
            int processingAttempt,
            Instant expiresAt,
            ReserveMaterialLineRequest line,
            InventoryItem item,
            InventoryLocation location,
            List<InventoryBalance> candidates,
            BigDecimal requiredQuantity) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderId(orderId);
        reservation.setOrderItemId(line.orderItemId());
        reservation.setProductVariantId(line.productVariantId());
        reservation.setIngredientId(line.ingredientId());
        reservation.setInventoryItem(item);
        reservation.setLocation(location);
        reservation.setQuantity(requiredQuantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setReservationType(ReservationType.MATERIAL);
        reservation.setProcessingAttempt(processingAttempt);
        reservation.setExpiresAt(expiresAt == null ? Instant.parse("9999-12-31T00:00:00Z") : expiresAt);

        BigDecimal remaining = requiredQuantity;
        List<PendingMaterialAllocation> allocations = new ArrayList<>();
        for (InventoryBalance balance : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = balance.getAvailableQuantity();
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal allocatedQuantity = available.min(remaining);
            InventoryReservationBatchAllocation allocation = new InventoryReservationBatchAllocation();
            allocation.setInventoryBatch(balance.getInventoryBatch());
            allocation.setQuantity(allocatedQuantity);
            allocation.setStatus(ReservationStatus.RESERVED);
            reservation.addBatchAllocation(allocation);
            allocations.add(new PendingMaterialAllocation(balance, allocatedQuantity));
            remaining = remaining.subtract(allocatedQuantity);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new OutOfStockException("OUT_OF_STOCK materialInventoryItemId=" + item.getId());
        }
        return new PendingMaterialReservation(reservation, allocations);
    }

    private void releaseMaterialReservation(InventoryReservation reservation) {
        reservation.getBatchAllocations().forEach(allocation -> {
            InventoryBalance balance = balanceRepository.findForUpdateByBatchId(
                            allocation.getInventoryBatch().getId(),
                            reservation.getLocation().getId())
                    .orElseThrow(() -> new BadRequestException("Reserved batch balance not found"));
            BigDecimal quantity = normalizeQuantity(allocation.getQuantity());
            if (balance.getQuantityReserved().compareTo(quantity) < 0) {
                throw new BadRequestException("Reserved material cannot be released");
            }
            balance.setQuantityReserved(balance.getQuantityReserved().subtract(quantity));
            allocation.setStatus(ReservationStatus.RELEASED);
        });
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setQuantity(BigDecimal.ZERO);
    }

    private record PendingMaterialReservation(
            InventoryReservation reservation,
            List<PendingMaterialAllocation> allocations
    ) {
        private void applyToBalances() {
            allocations.forEach(allocation -> allocation.balance().setQuantityReserved(
                    allocation.balance().getQuantityReserved().add(allocation.quantity())));
        }
    }

    private record PendingMaterialAllocation(InventoryBalance balance, BigDecimal quantity) {
    }

    private record MaterialKey(UUID inventoryItemId, UUID locationId) {
    }

    private record MaterialRequirement(ReserveMaterialLineRequest line, InventoryItem item,
                                       InventoryLocation location, BigDecimal quantity) {
    }

    @Override
    public List<InventoryReservationResponse> getExpiringReservations(int withinMinutes) {
        java.time.Instant deadline = java.time.Instant.now().plus(withinMinutes > 0 ? withinMinutes : 60, java.time.temporal.ChronoUnit.MINUTES);
        return reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, deadline)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}
