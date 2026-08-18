package org.hvtra.iventoryservice.reservation.service;

import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
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
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;

    public InventoryReservationServiceImpl(
            InventoryReservationRepository reservationRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository) {
        this.reservationRepository = reservationRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
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
        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED);
        if (reservations.isEmpty()) {
            List<InventoryReservation> confirmed =
                    reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.CONFIRMED);
            if (!confirmed.isEmpty()) {
                return confirmed.stream().map(this::toResponse).toList();
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
                                quantity);
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
    public int expireReservations() {
        List<InventoryReservation> reservations = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED,
                Instant.now());
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
        reservation.setQuantity(BigDecimal.ZERO);
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }

    private void reserve(UUID itemId, UUID locationId, BigDecimal quantity, UUID productVariantId) {
        int updated = balanceRepository.reserveAvailable(itemId, locationId, quantity);
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

    private InventoryLocation resolveApplyLocation(ApplyInventoryReservationRequest request) {
        if (request.locationId() != null) {
            return activeLocation(request.locationId(), request.branchId());
        }

        List<InventoryReservation> existingReservations =
                reservationRepository.findByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED)
                        .stream()
                        .filter(reservation -> reservation.getProductVariantId().equals(request.productVariantId()))
                        .toList();
        if (existingReservations.size() == 1) {
            return existingReservations.get(0).getLocation();
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
            return activeLocation(locationId, branchId);
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
        if (branchId != null && !location.getBranchId().equals(branchId)) {
            throw new BadRequestException("Inventory location " + locationId + " does not belong to branch " + branchId);
        }
        return location;
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
}
