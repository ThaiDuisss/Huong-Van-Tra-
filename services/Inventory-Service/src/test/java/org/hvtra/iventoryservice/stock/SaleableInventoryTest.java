package org.hvtra.iventoryservice.stock;

import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.dto.request.StoreStockOutRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreTransferRequest;
import org.hvtra.iventoryservice.movement.mapper.InventoryTransactionMapper;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.movement.service.InventoryTransactionServiceImpl;
import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationServiceImpl;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.stock.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.stock.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.stock.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.stock.dto.response.InventoryBalanceWarehouseResponse;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.stock.mapper.InventoryBalanceMapper;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.stock.service.InventoryBalanceServiceImpl;
import org.hvtra.iventoryservice.stock.service.InventoryStockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleableInventoryTest {

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryLocationRepository locationRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private InventoryBatchRepository batchRepository;

    @Mock
    private InventoryTransactionMapper transactionMapper;

    @Mock
    private InventoryBalanceMapper balanceMapper;

    @Mock
    private CatalogClient catalogClient;

    private UUID branchId;
    private UUID variantId;
    private InventoryItem item;
    private InventoryLocation warehouse;
    private InventoryLocation shelf;
    private InventoryLocation shelfTarget;
    private InventoryLocation damaged;
    private InventoryLocation quarantine;

    @BeforeEach
    void setUp() {
        branchId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        item = item(variantId);
        warehouse = location(InventoryLocationType.WAREHOUSE, branchId, null);
        shelf = location(InventoryLocationType.SHELF, branchId, warehouse);
        shelfTarget = location(InventoryLocationType.SHELF, branchId, warehouse);
        damaged = location(InventoryLocationType.DAMAGED, branchId, warehouse);
        quarantine = location(InventoryLocationType.QUARANTINE, branchId, warehouse);
    }

    @Test
    void availableQuantitySubtractsReservedAndBlockedAndClampsToZero() {
        InventoryBalance balance = balance(item, warehouse, "10.00", "2.00", "3.00");

        assertThat(balance.getAvailableQuantity()).isEqualByComparingTo("5.00");

        balance.setQuantityBlocked(new BigDecimal("20.00"));
        assertThat(balance.getAvailableQuantity()).isEqualByComparingTo("0.00");
    }

    @Test
    void internalPosAvailabilitySubtractsBlockedAndIgnoresDamagedAndQuarantineLocations() {
        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.PRODUCT_VARIANT)))
                .thenReturn(List.of(item));
        when(balanceRepository.findSaleableBalancesByInventoryItemIds(
                eq(List.of(item.getId())),
                eq(InventoryStatus.ACTIVE),
                any()))
                .thenReturn(List.of(
                        balance(item, warehouse, "10.00", "2.00", "3.00"),
                        balance(item, damaged, "99.00", "0.00", "0.00"),
                        balance(item, quarantine, "99.00", "0.00", "0.00")));

        InventoryStockServiceImpl service = new InventoryStockServiceImpl(
                balanceRepository,
                itemRepository,
                catalogClient);

        List<StockVariantBatchResponse> responses = service.getStocksByVariantIds(
                new StockVariantBatchRequest(List.of(variantId), null));

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().availableQuantity()).isEqualByComparingTo("5.00");
        assertThat(responses.getFirst().inventoryLocationId()).isEqualTo(warehouse.getId());
    }

    @Test
    void productAvailabilitySubtractsBlockedQuantity() {
        UUID productId = UUID.randomUUID();
        when(catalogClient.getVariantRefsByProductIds(List.of(productId)))
                .thenReturn(List.of(new CatalogClient.ProductVariantProductRefResponse(variantId, productId)));
        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.PRODUCT_VARIANT)))
                .thenReturn(List.of(item));
        when(balanceRepository.findSaleableBalancesByInventoryItemIds(
                eq(List.of(item.getId())),
                eq(InventoryStatus.ACTIVE),
                any()))
                .thenReturn(List.of(balance(item, warehouse, "10.00", "2.00", "3.00")));

        InventoryStockServiceImpl service = new InventoryStockServiceImpl(
                balanceRepository,
                itemRepository,
                catalogClient);

        List<StockBatchResponse> responses = service.getStocksByProductIds(
                new StockBatchRequest(List.of(productId), null));

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().availableQuantity()).isEqualByComparingTo("5.00");
    }

    @Test
    void internalPosAvailabilityIsOutOfStockWhenOnlyNonSaleableLocationsExist() {
        when(itemRepository.findByReferenceIdInAndItemType(List.of(variantId), InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(List.of(item));
        when(balanceRepository.findSaleableBalancesByInventoryItemIds(
                eq(List.of(item.getId())),
                eq(InventoryStatus.ACTIVE),
                any()))
                .thenReturn(List.of(
                        balance(item, damaged, "10.00", "0.00", "0.00"),
                        balance(item, quarantine, "10.00", "0.00", "0.00")));

        InventoryStockServiceImpl service = new InventoryStockServiceImpl(
                balanceRepository,
                itemRepository,
                catalogClient);

        List<StockVariantBatchResponse> responses = service.getStocksByVariantIds(
                new StockVariantBatchRequest(List.of(variantId), null));

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().availableQuantity()).isEqualByComparingTo("0.00");
        assertThat(responses.getFirst().stockStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void searchViewPosSubtractsBlockedAndDoesNotAggregateDamagedOrQuarantineChildren() {
        when(locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(warehouse));
        when(balanceRepository.findAll(anyBalanceSpecification()))
                .thenReturn(List.of(
                        balance(item, warehouse, "10.00", "2.00", "3.00"),
                        balance(item, damaged, "7.00", "0.00", "0.00"),
                        balance(item, quarantine, "8.00", "0.00", "0.00")));

        InventoryBalanceServiceImpl service = new InventoryBalanceServiceImpl(
                balanceRepository,
                itemRepository,
                locationRepository,
                balanceMapper,
                catalogClient);

        List<InventoryBalanceWarehouseResponse> responses = service.findWarehouseBalances(null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().quantityOnHand()).isEqualByComparingTo("10.00");
        assertThat(responses.getFirst().availableQuantity()).isEqualByComparingTo("5.00");
    }

    @Test
    void reservationRejectsExplicitDamagedLocation() {
        InventoryReservationServiceImpl service = reservationService();
        ApplyInventoryReservationRequest request = reservationRequest(damaged.getId(), "1.00");
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findById(damaged.getId())).thenReturn(Optional.of(damaged));

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Inventory location is not saleable");
        verify(balanceRepository, never()).reserveAvailable(any(), any(), any(), any(), any());
    }

    @Test
    void reservationRejectsExplicitQuarantineLocation() {
        InventoryReservationServiceImpl service = reservationService();
        ApplyInventoryReservationRequest request = reservationRequest(quarantine.getId(), "1.00");
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findById(quarantine.getId())).thenReturn(Optional.of(quarantine));

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Inventory location is not saleable");
        verify(balanceRepository, never()).reserveAvailable(any(), any(), any(), any(), any());
    }

    @Test
    void reservationCannotReserveBlockedQuantity() {
        InventoryReservationServiceImpl service = reservationService();
        UUID orderId = UUID.randomUUID();
        ApplyInventoryReservationRequest request = new ApplyInventoryReservationRequest(
                orderId,
                branchId,
                variantId,
                warehouse.getId(),
                new BigDecimal("8.00"),
                Instant.now().plusSeconds(60));
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(reservationRepository.findByOrderIdAndProductVariantIdAndLocationId(orderId, variantId, warehouse.getId()))
                .thenReturn(Optional.empty());
        when(balanceRepository.reserveAvailable(eq(item.getId()), eq(warehouse.getId()), eq(new BigDecimal("8.00")), eq(InventoryStatus.ACTIVE), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    void normalWarehouseReservationStillWorks() {
        InventoryReservationServiceImpl service = reservationService();
        UUID orderId = UUID.randomUUID();
        ApplyInventoryReservationRequest request = new ApplyInventoryReservationRequest(
                orderId,
                branchId,
                variantId,
                warehouse.getId(),
                new BigDecimal("5.00"),
                Instant.now().plusSeconds(60));
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(reservationRepository.findByOrderIdAndProductVariantIdAndLocationId(orderId, variantId, warehouse.getId()))
                .thenReturn(Optional.empty());
        when(balanceRepository.reserveAvailable(eq(item.getId()), eq(warehouse.getId()), eq(new BigDecimal("5.00")), eq(InventoryStatus.ACTIVE), any()))
                .thenReturn(1);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.apply(request).reservedQuantity()).isEqualByComparingTo("5.00");
    }

    @Test
    void saleStockOutCannotUseBlockedQuantity() {
        InventoryTransactionServiceImpl service = transactionService();
        when(locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(warehouse));
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(balanceRepository.findForUpdate(item.getId(), warehouse.getId()))
                .thenReturn(Optional.of(balance(item, warehouse, "10.00", "0.00", "9.00")));

        assertThatThrownBy(() -> service.storeStockOut(stockOutRequest("2.00")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Saleable stock is not enough");
    }

    @Test
    void normalWarehouseSaleStockOutStillWorks() {
        InventoryTransactionServiceImpl service = transactionService();
        InventoryBalance balance = balance(item, warehouse, "10.00", "2.00", "3.00");
        when(locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(warehouse));
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(balanceRepository.findForUpdate(item.getId(), warehouse.getId())).thenReturn(Optional.of(balance));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.storeStockOut(stockOutRequest("5.00"));

        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("5.00");
    }

    @Test
    void storeTransferCannotMoveBlockedQuantity() {
        InventoryTransactionServiceImpl service = transactionService();
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));
        when(locationRepository.findById(shelfTarget.getId())).thenReturn(Optional.of(shelfTarget));
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        InventoryBalance sourceBalance = balance(item, shelf, "10.00", "0.00", "9.00");
        when(balanceRepository.findForUpdate(eq(item.getId()), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID locationId = invocation.getArgument(1);
                    return shelf.getId().equals(locationId) ? Optional.of(sourceBalance) : Optional.empty();
                });

        assertThatThrownBy(() -> service.storeTransfer(new StoreTransferRequest(
                shelf.getId(),
                shelfTarget.getId(),
                item.getId(),
                new BigDecimal("2.00"),
                "move")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kh");
    }

    private InventoryReservationServiceImpl reservationService() {
        return new InventoryReservationServiceImpl(
                reservationRepository,
                itemRepository,
                locationRepository,
                balanceRepository);
    }

    private InventoryTransactionServiceImpl transactionService() {
        return new InventoryTransactionServiceImpl(
                transactionRepository,
                itemRepository,
                locationRepository,
                balanceRepository,
                batchRepository,
                transactionMapper);
    }

    private ApplyInventoryReservationRequest reservationRequest(UUID locationId, String quantity) {
        return new ApplyInventoryReservationRequest(
                UUID.randomUUID(),
                branchId,
                variantId,
                locationId,
                new BigDecimal(quantity),
                Instant.now().plusSeconds(60));
    }

    private StoreStockOutRequest stockOutRequest(String quantity) {
        return new StoreStockOutRequest(
                InventoryReferenceType.ORDER,
                UUID.randomUUID().toString(),
                List.of(new StoreStockOutRequest.LineRequest(item.getId(), new BigDecimal(quantity))));
    }

    private InventoryItem item(UUID referenceId) {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setId(UUID.randomUUID());
        inventoryItem.setReferenceId(referenceId);
        inventoryItem.setReferenceCode("SKU-" + referenceId.toString().substring(0, 8));
        inventoryItem.setName("Tra sen");
        inventoryItem.setItemType(InventoryItemType.PRODUCT_VARIANT);
        inventoryItem.setStatus(InventoryStatus.ACTIVE);
        inventoryItem.setReorderPoint(5);
        return inventoryItem;
    }

    private InventoryLocation location(
            InventoryLocationType type,
            UUID locationBranchId,
            InventoryLocation parentLocation) {
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setBranchId(locationBranchId);
        location.setType(type);
        location.setStatus(InventoryStatus.ACTIVE);
        location.setParentLocation(parentLocation);
        location.setCode(type.name() + "-" + location.getId().toString().substring(0, 8));
        location.setName(type.name());
        return location;
    }

    private InventoryBalance balance(
            InventoryItem inventoryItem,
            InventoryLocation location,
            String onHand,
            String reserved,
            String blocked) {
        InventoryBalance balance = new InventoryBalance();
        balance.setId(UUID.randomUUID());
        balance.setInventoryItem(inventoryItem);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(onHand));
        balance.setQuantityReserved(new BigDecimal(reserved));
        balance.setQuantityBlocked(new BigDecimal(blocked));
        return balance;
    }

    private Specification<InventoryBalance> anyBalanceSpecification() {
        return any();
    }
}
