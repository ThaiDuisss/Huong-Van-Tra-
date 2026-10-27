package org.hvtra.iventoryservice.sale;

import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
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
import org.hvtra.iventoryservice.sale.service.SaleFulfillmentServiceImpl;
import org.hvtra.iventoryservice.shared.exception.OutOfStockException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleFulfillmentServiceImplTest {
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private InventoryReplenishmentAlertRepository alertRepository;
    @Mock
    private ShelfReplenishmentService replenishmentService;

    @InjectMocks
    private SaleFulfillmentServiceImpl service;

    private UUID orderId;
    private UUID variantId;
    private UUID ingredientId;
    private InventoryItem product;
    private InventoryItem material;
    private InventoryLocation warehouse;
    private InventoryLocation shelf;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        product = item(variantId, InventoryItemType.PRODUCT_VARIANT, "Trà sen 100g", null);
        material = item(ingredientId, InventoryItemType.INGREDIENT, "Trà xanh", 10);
        warehouse = location(InventoryLocationType.WAREHOUSE);
        shelf = location(InventoryLocationType.SHELF);

        when(transactionRepository.existsByTransactionCode(any())).thenReturn(false);
        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.PRODUCT_VARIANT)))
                .thenReturn(List.of(product));
        when(itemRepository.findByReferenceIdAndItemType(ingredientId, InventoryItemType.INGREDIENT))
                .thenReturn(Optional.of(material));
        when(locationRepository.findFirstByTypeAndStatusOrderByCreatedAtAsc(
                InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)).thenReturn(Optional.of(warehouse));
        when(locationRepository.findByParentLocation_IdAndTypeAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                warehouse.getId(), InventoryLocationType.SHELF, InventoryStatus.ACTIVE)).thenReturn(List.of(shelf));
        when(balanceRepository.sumAvailableByItemsAndLocations(any(), any())).thenReturn(List.of());
        when(alertRepository.existsOpenAlert(any(), any(), any())).thenReturn(false);
    }

    @Test
    void fulfill_deductsShelfFefoWithoutTouchingWarehouseWhenShelfHasEnough() {
        InventoryBalance early = balance(product, shelf, "20");
        InventoryBalance late = balance(product, shelf, "30");
        stubShelf(product, early, late);

        FulfillSaleResponse response = service.fulfill(request("30", null));

        assertThat(early.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(late.getQuantityOnHand()).isEqualByComparingTo("20");
        assertThat(response.replenishmentCode()).isNull();
        assertThat(response.lines().getFirst().movedFromWarehouse()).isEqualByComparingTo("0");
        // Không thiếu thì vẫn gọi để hủy phiếu còn mở của đơn, với danh sách thiếu rỗng.
        verify(replenishmentService).autoIssueForOrder(orderId, null, Map.of());

        ArgumentCaptor<InventoryTransaction> saved = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(InventoryTransactionType.SALE_OUT);
        assertThat(saved.getValue().getTransactionCode()).isEqualTo("SALE-" + orderId);
        assertThat(saved.getValue().getLines()).hasSize(2);
    }

    @Test
    void fulfill_issuesWarehouseStockToShelfForTheShortageThenDeducts() {
        InventoryBalance onShelf = balance(product, shelf, "30");
        InventoryBalance inWarehouse = balance(product, warehouse, "60");
        when(balanceRepository.findFefoAvailableForUpdate(eq(product.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(inWarehouse));
        // Trước khi xuất kho kệ có 30; sau khi phiếu nhập kệ chuyển 20 lên thì kệ có 50.
        InventoryBalance moved = balance(product, shelf, "20");
        when(balanceRepository.findFefoAvailableForUpdateInLocations(eq(product.getId()), any(), any(), any()))
                .thenReturn(List.of(onShelf))
                .thenReturn(List.of(onShelf, moved));
        when(replenishmentService.autoIssueForOrder(eq(orderId), any(), any()))
                .thenReturn(slip("NKE-00007", shelf.getId()));

        FulfillSaleResponse response = service.fulfill(request("50", null));

        verify(replenishmentService).autoIssueForOrder(orderId, null, Map.of(variantId, new BigDecimal("20")));
        assertThat(response.replenishmentCode()).isEqualTo("NKE-00007");
        assertThat(response.lines().getFirst().movedFromWarehouse()).isEqualByComparingTo("20");
        assertThat(onShelf.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(moved.getQuantityOnHand()).isEqualByComparingTo("0");
    }

    @Test
    void fulfill_rejectsWhenShelfPlusWarehouseIsNotEnoughAndWritesNothing() {
        stubShelf(product, balance(product, shelf, "30"));
        when(balanceRepository.findFefoAvailableForUpdate(eq(product.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(balance(product, warehouse, "10")));

        assertThatThrownBy(() -> service.fulfill(request("50", null)))
                .isInstanceOf(OutOfStockException.class)
                .hasMessageContaining("trên kệ 30")
                .hasMessageContaining("trong kho 10");

        verify(replenishmentService, never()).autoIssueForOrder(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fulfill_isIdempotentForTheSameOrder() {
        when(transactionRepository.existsByTransactionCode("SALE-" + orderId)).thenReturn(true);

        FulfillSaleResponse response = service.fulfill(request("5", null));

        assertThat(response.alreadyProcessed()).isTrue();
        verify(balanceRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fulfill_reservesBomMaterialsForRestockWithoutConsumingThem() {
        stubShelf(product, balance(product, shelf, "30"));
        InventoryBalance tea = balance(material, warehouse, "100");
        when(balanceRepository.findFefoAvailableForUpdate(eq(material.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(tea));

        FulfillSaleResponse response = service.fulfill(request("30", "3"));

        assertThat(tea.getQuantityReserved()).isEqualByComparingTo("3");
        assertThat(tea.getQuantityOnHand()).isEqualByComparingTo("100");
        ArgumentCaptor<InventoryReservation> saved = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getReservationType()).isEqualTo(ReservationType.MATERIAL);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getValue().getProductVariantId()).isEqualTo(variantId);
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(response.materials().getFirst().shortageQuantity()).isEqualByComparingTo("0");
        assertThat(response.materials().getFirst().alertRaised()).isFalse();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void fulfill_raisesReorderAlertWhenMaterialDropsToThreshold() {
        stubShelf(product, balance(product, shelf, "30"));
        InventoryBalance tea = balance(material, warehouse, "12");
        when(balanceRepository.findFefoAvailableForUpdate(eq(material.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(tea));

        FulfillSaleResponse response = service.fulfill(request("30", "3"));

        assertThat(response.materials().getFirst().availableAfter()).isEqualByComparingTo("9");
        assertThat(response.materials().getFirst().alertRaised()).isTrue();
        ArgumentCaptor<InventoryReplenishmentAlert> alert = ArgumentCaptor.forClass(InventoryReplenishmentAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getAlertType()).isEqualTo(InventoryReplenishmentAlertType.REORDER_REQUIRED);
        assertThat(alert.getValue().getInventoryItem()).isSameAs(material);
    }

    @Test
    void fulfill_stillSellsWhenMaterialIsShortAndRaisesOutOfStockAlert() {
        InventoryBalance onShelf = balance(product, shelf, "30");
        stubShelf(product, onShelf);
        InventoryBalance tea = balance(material, warehouse, "2");
        when(balanceRepository.findFefoAvailableForUpdate(eq(material.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(tea));

        FulfillSaleResponse response = service.fulfill(request("30", "5"));

        assertThat(onShelf.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(tea.getQuantityReserved()).isEqualByComparingTo("2");
        assertThat(response.materials().getFirst().shortageQuantity()).isEqualByComparingTo("3");
        ArgumentCaptor<InventoryReplenishmentAlert> alert = ArgumentCaptor.forClass(InventoryReplenishmentAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getAlertType()).isEqualTo(InventoryReplenishmentAlertType.OUT_OF_STOCK);
    }

    @Test
    void fulfill_doesNotDuplicateAnOpenMaterialAlert() {
        stubShelf(product, balance(product, shelf, "30"));
        when(balanceRepository.findFefoAvailableForUpdate(eq(material.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(balance(material, warehouse, "12")));
        when(alertRepository.existsOpenAlert(eq(material.getId()), eq(warehouse.getId()), any())).thenReturn(true);

        FulfillSaleResponse response = service.fulfill(request("30", "3"));

        assertThat(response.materials().getFirst().alertRaised()).isFalse();
        verify(alertRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- fixtures

    private void stubShelf(InventoryItem item, InventoryBalance... balances) {
        when(balanceRepository.findFefoAvailableForUpdateInLocations(eq(item.getId()), any(), any(), any()))
                .thenReturn(List.of(balances));
    }

    private FulfillSaleRequest request(String quantity, String materialQuantity) {
        return new FulfillSaleRequest(
                orderId, null, 1,
                List.of(new FulfillSaleRequest.Line(variantId, new BigDecimal(quantity))),
                materialQuantity == null ? List.of() : List.of(
                        new FulfillSaleRequest.MaterialLine(variantId, ingredientId, new BigDecimal(materialQuantity))));
    }

    private ShelfReplenishmentResponse slip(String code, UUID shelfId) {
        return new ShelfReplenishmentResponse(UUID.randomUUID(), code, InventoryDocumentStatus.POSTED, null,
                warehouse.getId(), "Kho", shelfId, "Kệ", orderId.toString(), null, BigDecimal.ONE, 1,
                null, null, null, null, null, true, true, null, null, List.of(), null, null, null);
    }

    private InventoryItem item(UUID referenceId, InventoryItemType type, String name, Integer reorderPoint) {
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setReferenceId(referenceId);
        item.setItemType(type);
        item.setName(name);
        item.setStatus(InventoryStatus.ACTIVE);
        item.setReorderPoint(reorderPoint);
        return item;
    }

    private InventoryBalance balance(InventoryItem item, InventoryLocation location, String onHand) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(UUID.randomUUID());
        batch.setInventoryItem(item);
        batch.setExpiryDate(LocalDate.now().plusDays(30));
        InventoryBalance balance = new InventoryBalance();
        balance.setId(UUID.randomUUID());
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(onHand));
        balance.setQuantityReserved(BigDecimal.ZERO);
        balance.setQuantityBlocked(BigDecimal.ZERO);
        return balance;
    }

    private InventoryLocation location(InventoryLocationType type) {
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setBranchId(UUID.randomUUID());
        location.setType(type);
        location.setStatus(InventoryStatus.ACTIVE);
        location.setName(type.name());
        location.setCode(type.name());
        return location;
    }
}
