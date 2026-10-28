package org.hvtra.iventoryservice.reservation;

import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.entity.InventoryReservationBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialLineRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialOrderRequest;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationServiceImpl;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceImplTest {

    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;

    private InventoryReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryReservationServiceImpl(
                reservationRepository,
                itemRepository,
                locationRepository,
                balanceRepository,
                transactionRepository);
    }

    @Test
    void reserveMaterials_splitsAcrossBatchesInFefoOrder() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance batchA = balance(item, location, "A", "600", "0", LocalDate.now().plusDays(3));
        InventoryBalance batchB = balance(item, location, "B", "1000", "0", LocalDate.now().plusDays(10));
        ReserveMaterialOrderRequest request = request(
                orderId,
                variantId,
                ingredientId,
                item.getId(),
                location.getId(),
                "1000");
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));
        when(balanceRepository.findFefoAvailableForUpdate(
                item.getId(),
                location.getId(),
                InventoryStatus.ACTIVE,
                LocalDate.now()))
                .thenReturn(List.of(batchA, batchB));
        when(reservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserveMaterials(request);

        ArgumentCaptor<List<InventoryReservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        InventoryReservation reservation = captor.getValue().getFirst();
        assertEquals(new BigDecimal("600"), reservation.getBatchAllocations().get(0).getQuantity());
        assertEquals(new BigDecimal("400"), reservation.getBatchAllocations().get(1).getQuantity());
        assertEquals(new BigDecimal("600"), batchA.getQuantityReserved());
        assertEquals(new BigDecimal("400"), batchB.getQuantityReserved());
        assertEquals(1, reservation.getProcessingAttempt());
    }

    @Test
    void reserveMaterials_rollsBackWhenAnyMaterialInsufficient() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance batch = balance(item, location, "A", "600", "0", LocalDate.now().plusDays(3));
        ReserveMaterialOrderRequest request = request(
                orderId,
                variantId,
                ingredientId,
                item.getId(),
                location.getId(),
                "1000");
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));
        when(balanceRepository.findFefoAvailableForUpdate(
                item.getId(),
                location.getId(),
                InventoryStatus.ACTIVE,
                LocalDate.now()))
                .thenReturn(List.of(batch));

        assertThrows(OutOfStockException.class, () -> service.reserveMaterials(request));

        assertEquals(BigDecimal.ZERO, batch.getQuantityReserved());
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void reserveMaterials_aggregatesDuplicateMaterialLinesBeforeAllocating() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance batch = balance(item, location, "A", "1000", "0", LocalDate.now().plusDays(3));
        ReserveMaterialLineRequest first = new ReserveMaterialLineRequest(null, variantId, ingredientId,
                item.getId(), location.getId(), new BigDecimal("600"));
        ReserveMaterialLineRequest second = new ReserveMaterialLineRequest(null, variantId, ingredientId,
                item.getId(), location.getId(), new BigDecimal("500"));
        ReserveMaterialOrderRequest request = new ReserveMaterialOrderRequest(orderId, null, 1, null,
                List.of(first, second));
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));
        when(balanceRepository.findFefoAvailableForUpdate(item.getId(), location.getId(),
                InventoryStatus.ACTIVE, LocalDate.now())).thenReturn(List.of(batch));

        assertThrows(OutOfStockException.class, () -> service.reserveMaterials(request));

        assertEquals(BigDecimal.ZERO, batch.getQuantityReserved());
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void reserveMaterials_rejectsLocationWithoutRequestedBranch() {
        UUID branchId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        ReserveMaterialLineRequest line = new ReserveMaterialLineRequest(null, UUID.randomUUID(),
                UUID.randomUUID(), item.getId(), location.getId(), BigDecimal.ONE);
        ReserveMaterialOrderRequest request = new ReserveMaterialOrderRequest(UUID.randomUUID(), branchId, 1,
                null, List.of(line));
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));

        assertThrows(BadRequestException.class, () -> service.reserveMaterials(request));

        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void consumeOrder_decrementsOnHandAndReservedForSameBatchAllocations() {
        UUID orderId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance balance = balance(item, location, "A", "1000", "600", LocalDate.now().plusDays(3));
        InventoryReservation reservation = materialReservation(orderId, item, location, balance, "600");
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of(reservation));
        when(balanceRepository.findForUpdateByBatchId(balance.getInventoryBatch().getId(), location.getId()))
                .thenReturn(Optional.of(balance));
        when(reservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.consumeMaterials(new ReleaseInventoryReservationOrderRequest(orderId));

        assertEquals(new BigDecimal("400"), balance.getQuantityOnHand());
        assertEquals(BigDecimal.ZERO, balance.getQuantityReserved());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getBatchAllocations().getFirst().getStatus());
        org.mockito.ArgumentCaptor<InventoryTransaction> movement = org.mockito.ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(movement.capture());
        assertEquals("SALE_OUT", movement.getValue().getType().name());
        assertEquals(orderId.toString(), movement.getValue().getReferenceId());
        assertEquals(new BigDecimal("600"), movement.getValue().getLines().getFirst().getQuantity());
    }

    @Test
    void consumeOrder_isIdempotentWhenAlreadyConfirmed() {
        UUID orderId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance balance = balance(item, location, "A", "400", "0", LocalDate.now().plusDays(3));
        InventoryReservation confirmed = materialReservation(orderId, item, location, balance, "600");
        confirmed.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of(), List.of(confirmed));

        service.consumeMaterials(new ReleaseInventoryReservationOrderRequest(orderId));

        verifyNoInteractions(balanceRepository);
        verifyNoInteractions(transactionRepository);
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void confirmOrder_isNoOpForFulfilledCounterSaleWithoutReservations() {
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(transactionRepository.existsByTransactionCode("SALE-" + orderId)).thenReturn(true);

        assertEquals(List.of(), service.confirmOrder(new ConfirmInventoryReservationOrderRequest(orderId)));
        verifyNoInteractions(balanceRepository);
    }

    @Test
    void confirmOrder_rejectsOrderWithoutReservationOrSale() {
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        when(transactionRepository.existsByTransactionCode("SALE-" + orderId)).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.confirmOrder(new ConfirmInventoryReservationOrderRequest(orderId)));
    }

    @Test
    void releaseOrder_decrementsReservedOnly() {
        UUID orderId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance balance = balance(item, location, "A", "1000", "600", LocalDate.now().plusDays(3));
        InventoryReservation reservation = materialReservation(orderId, item, location, balance, "600");
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of(reservation));
        when(balanceRepository.findForUpdateByBatchId(balance.getInventoryBatch().getId(), location.getId()))
                .thenReturn(Optional.of(balance));
        when(reservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.releaseMaterials(new ReleaseInventoryReservationOrderRequest(orderId));

        assertEquals(new BigDecimal("1000"), balance.getQuantityOnHand());
        assertEquals(BigDecimal.ZERO, balance.getQuantityReserved());
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
        assertEquals(ReservationStatus.RELEASED, reservation.getBatchAllocations().getFirst().getStatus());
    }

    @Test
    void releaseOrder_isIdempotentWhenAlreadyReleased() {
        UUID orderId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance balance = balance(item, location, "A", "1000", "0", LocalDate.now().plusDays(3));
        InventoryReservation released = materialReservation(orderId, item, location, balance, "600");
        released.setStatus(ReservationStatus.RELEASED);
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(any(), any(), any()))
                .thenReturn(List.of(), List.of(released));

        service.releaseMaterials(new ReleaseInventoryReservationOrderRequest(orderId));

        verifyNoInteractions(balanceRepository);
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void releaseOrderReportsAlreadyConsumedReservation() {
        UUID orderId = UUID.randomUUID();
        InventoryItem item = item(UUID.randomUUID());
        InventoryLocation location = location(UUID.randomUUID());
        InventoryBalance balance = balance(item, location, "A", "400", "0", LocalDate.now().plusDays(3));
        InventoryReservation consumed = materialReservation(orderId, item, location, balance, "600");
        consumed.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(orderId,
                org.hvtra.iventoryservice.domain.enums.ReservationType.MATERIAL, ReservationStatus.RESERVED))
                .thenReturn(List.of());
        when(reservationRepository.findByOrderIdAndReservationTypeAndStatus(orderId,
                org.hvtra.iventoryservice.domain.enums.ReservationType.MATERIAL, ReservationStatus.CONFIRMED))
                .thenReturn(List.of(consumed));

        var response = service.releaseMaterials(new ReleaseInventoryReservationOrderRequest(orderId));

        assertEquals(ReservationStatus.CONFIRMED, response.getFirst().status());
        verifyNoInteractions(balanceRepository);
    }

    private ReserveMaterialOrderRequest request(
            UUID orderId,
            UUID variantId,
            UUID ingredientId,
            UUID inventoryItemId,
            UUID locationId,
            String quantity) {
        return new ReserveMaterialOrderRequest(
                orderId,
                null,
                1,
                Instant.parse("2026-09-26T10:00:00Z"),
                List.of(new ReserveMaterialLineRequest(
                        UUID.randomUUID(),
                        variantId,
                        ingredientId,
                        inventoryItemId,
                        locationId,
                        new BigDecimal(quantity))));
    }

    private InventoryItem item(UUID id) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setItemType(InventoryItemType.INGREDIENT);
        item.setStatus(InventoryStatus.ACTIVE);
        return item;
    }

    private InventoryLocation location(UUID id) {
        InventoryLocation location = new InventoryLocation();
        location.setId(id);
        location.setType(InventoryLocationType.WAREHOUSE);
        location.setStatus(InventoryStatus.ACTIVE);
        return location;
    }

    private InventoryBalance balance(
            InventoryItem item,
            InventoryLocation location,
            String batchCode,
            String onHand,
            String reserved,
            LocalDate expiryDate) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(UUID.randomUUID());
        batch.setInventoryItem(item);
        batch.setBatchCode(batchCode);
        batch.setStatus(InventoryBatchStatus.AVAILABLE);
        batch.setExpiryDate(expiryDate);
        batch.setReceivedAt(Instant.parse("2026-09-01T00:00:00Z"));
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(onHand));
        balance.setQuantityReserved(new BigDecimal(reserved));
        balance.setQuantityBlocked(BigDecimal.ZERO);
        return balance;
    }

    private InventoryReservation materialReservation(
            UUID orderId,
            InventoryItem item,
            InventoryLocation location,
            InventoryBalance balance,
            String quantity) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderId(orderId);
        reservation.setProductVariantId(UUID.randomUUID());
        reservation.setIngredientId(item.getReferenceId());
        reservation.setInventoryItem(item);
        reservation.setLocation(location);
        reservation.setQuantity(new BigDecimal(quantity));
        reservation.setStatus(ReservationStatus.RESERVED);
        InventoryReservationBatchAllocation allocation = new InventoryReservationBatchAllocation();
        allocation.setInventoryBatch(balance.getInventoryBatch());
        allocation.setQuantity(new BigDecimal(quantity));
        allocation.setStatus(ReservationStatus.RESERVED);
        reservation.addBatchAllocation(allocation);
        return reservation;
    }
}
