package org.hvtra.iventoryservice.availability.service;

import org.hvtra.iventoryservice.availability.AvailabilityProperties;
import org.hvtra.iventoryservice.availability.AvailabilityScenario;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateRequest;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.LineEstimate;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.document.GoodsReceipt;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.stock.service.InventoryStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityEstimateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");

    @Mock
    private InventoryStockService stockService;
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;
    @Mock
    private AvailabilityQueryRepository queries;

    private AvailabilityEstimateService service;
    private UUID variantId;
    private UUID ingredientId;
    private InventoryItem product;
    private InventoryItem material;

    @BeforeEach
    void setUp() {
        service = new AvailabilityEstimateService(stockService, catalogClient, itemRepository, balanceRepository,
                reservationRepository, queries, new AvailabilityProperties());
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

        variantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        product = item(variantId, "Trà sen hộp 100g", InventoryItemType.PRODUCT_VARIANT);
        material = item(ingredientId, "Trà xanh", InventoryItemType.INGREDIENT);

        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.PRODUCT_VARIANT)))
                .thenReturn(List.of(product));
        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.INGREDIENT)))
                .thenReturn(List.of(material));
        // Định mức: 1 hộp cần 0,1 kg trà, không hao hụt.
        when(catalogClient.getBomItems(variantId)).thenReturn(List.of(new CatalogClient.BomItemResponse(
                UUID.randomUUID(), new CatalogClient.IngredientRef(ingredientId, "Trà xanh"),
                new BigDecimal("0.1"), null, BigDecimal.ZERO)));
        when(queries.findOpenProductionOrders(eq(variantId), anyCollection())).thenReturn(List.of());
        when(queries.averageSupplyLeadSeconds(any())).thenReturn(null);
        when(queries.findOpenReceiptLines(anyCollection(), anyCollection())).thenReturn(List.of());
        when(reservationRepository
                .findByProductVariantIdAndInventoryItem_IdAndReservationTypeAndStatusOrderByCreatedAtAsc(
                        any(), any(), any(), any())).thenReturn(List.of());
        when(balanceRepository.findSellableBatchBalancesByInventoryItemIds(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void enoughStock_isAvailableNow() {
        stock("3", "7");

        AvailabilityEstimateResponse response = service.estimate(request("10"));

        assertThat(response.backorder()).isFalse();
        LineEstimate line = response.lines().get(0);
        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.AVAILABLE);
        assertThat(line.missingQuantity()).isEqualByComparingTo("0");
        verify(catalogClient, never()).getBomItems(any());
    }

    @Test
    void openProductionOrderCoversMissing_usesItsExpectedCompletionFromHistory() {
        stock("0", "2");
        // Thời gian sản xuất trung bình lưu sẵn trên mặt hàng: 3 ngày.
        product.setAvgSupplyLeadSeconds(Duration.ofDays(3).getSeconds());
        ProductionOrder running = production("LSX-001", "20", ProductionOrderStatus.PENDING_APPROVAL,
                NOW.minus(Duration.ofDays(1)), null);
        when(queries.findOpenProductionOrders(eq(variantId), anyCollection())).thenReturn(List.of(running));

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.IN_PRODUCTION);
        assertThat(line.missingQuantity()).isEqualByComparingTo("8");
        assertThat(line.productionOrderCode()).isEqualTo("LSX-001");
        // Tạo lệnh hôm qua + 3 ngày = 2 ngày nữa.
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(2)));
        assertThat(line.estimatedWaitDays()).isEqualTo(2);
        assertThat(line.durationSource()).isEqualTo("VARIANT_HISTORY");
        assertThat(line.depositRecommended()).isFalse();
        // Mặt hàng đã có số lưu sẵn thì không phải hỏi thêm gì về lịch sử.
        verify(queries, never()).averageSupplyLeadSeconds(any());
    }

    @Test
    void productionOrderReservedForAnotherOrder_isNotCounted() {
        stock("0", "0");
        ProductionOrder other = production("LSX-002", "20", ProductionOrderStatus.PENDING_APPROVAL, NOW, UUID.randomUUID());
        when(queries.findOpenProductionOrders(eq(variantId), anyCollection())).thenReturn(List.of(other));
        materialStock("5");

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS);
        assertThat(line.productionOrderCode()).isNull();
    }

    @Test
    void overdueProductionOrder_neverPromisesADateInThePast() {
        stock("0", "0");
        ProductionOrder late = production("LSX-003", "10", ProductionOrderStatus.PENDING_APPROVAL,
                NOW.minus(Duration.ofDays(9)), null);
        when(queries.findOpenProductionOrders(eq(variantId), anyCollection())).thenReturn(List.of(late));

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.IN_PRODUCTION);
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW);
        assertThat(line.estimatedWaitDays()).isZero();
    }

    @Test
    void materialsInStock_noHistory_addsOneDefaultProductionDay() {
        stock("0", "0");
        materialStock("1.0"); // cần 10 × 0,1 = 1,0

        AvailabilityEstimateResponse response = service.estimate(request("10"));
        LineEstimate line = response.lines().get(0);

        assertThat(response.backorder()).isTrue();
        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS);
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(line.durationSource()).isEqualTo("DEFAULT");
        assertThat(line.productionDays()).isEqualByComparingTo("1");
        assertThat(line.depositRecommended()).isFalse();
        assertThat(line.materials()).hasSize(1);
        assertThat(line.materials().get(0).arrivalSource()).isEqualTo("IN_STOCK");
        assertThat(response.estimatedAvailableAt()).isEqualTo(line.estimatedAvailableAt());
    }

    @Test
    void productionDuration_fallsBackToOtherProductsHistory() {
        stock("0", "0");
        materialStock("1.0");
        when(queries.averageSupplyLeadSeconds(InventoryItemType.PRODUCT_VARIANT))
                .thenReturn((double) Duration.ofHours(36).getSeconds());

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.durationSource()).isEqualTo("ALL_HISTORY");
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofHours(36)));
        assertThat(line.estimatedWaitDays()).isEqualTo(2);
    }

    @Test
    void materialsShort_openReceiptWithExpectedDate_arrivalPlusProduction() {
        stock("0", "0");
        materialStock("0.4"); // thiếu 0,6
        Instant expected = NOW.plus(Duration.ofDays(2));
        when(queries.findOpenReceiptLines(anyCollection(), anyCollection())).thenReturn(List.of(
                receiptLine("PN-001", InventoryDocumentStatus.APPROVED, "5", expected, NOW.minus(Duration.ofDays(1)))));

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.WAIT_MATERIAL_RECEIPT);
        assertThat(line.estimatedAvailableAt()).isEqualTo(expected.plus(Duration.ofDays(1)));
        assertThat(line.estimatedWaitDays()).isEqualTo(3);
        assertThat(line.depositRecommended()).isTrue();
        var materialEstimate = line.materials().get(0);
        assertThat(materialEstimate.missingQuantity()).isEqualByComparingTo("0.6");
        assertThat(materialEstimate.arrivalSource()).isEqualTo("OPEN_RECEIPT");
        assertThat(materialEstimate.receiptCode()).isEqualTo("PN-001");
        assertThat(materialEstimate.expectedArrivalAt()).isEqualTo(expected);
    }

    @Test
    void openReceiptWithoutExpectedDate_usesReceiptHistoryFromItsCreationDate() {
        stock("0", "0");
        materialStock("0");
        // Thời gian nhập trung bình lưu sẵn trên nguyên liệu: 4 ngày từ lúc lập phiếu tới lúc ghi sổ.
        material.setAvgSupplyLeadSeconds(Duration.ofDays(4).getSeconds());
        Instant created = NOW.minus(Duration.ofDays(1));
        when(queries.findOpenReceiptLines(anyCollection(), anyCollection())).thenReturn(List.of(
                receiptLine("PN-002", InventoryDocumentStatus.PENDING_APPROVAL, "5", null, created)));

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.WAIT_MATERIAL_RECEIPT);
        assertThat(line.materials().get(0).expectedArrivalAt()).isEqualTo(created.plus(Duration.ofDays(4)));
        assertThat(line.estimatedAvailableAt()).isEqualTo(created.plus(Duration.ofDays(5)));
    }

    @Test
    void openReceiptTooSmall_fallsBackToPurchaseLeadTime() {
        stock("0", "0");
        materialStock("0");
        when(queries.findOpenReceiptLines(anyCollection(), anyCollection())).thenReturn(List.of(
                receiptLine("PN-003", InventoryDocumentStatus.APPROVED, "0.5", NOW.plus(Duration.ofDays(1)), NOW)));

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.WAIT_MATERIAL_PURCHASE);
    }

    @Test
    void noReceipt_usesReceiptHistoryPlusProductionDuration() {
        stock("0", "0");
        materialStock("0");
        material.setAvgSupplyLeadSeconds(Duration.ofDays(4).getSeconds());
        product.setAvgSupplyLeadSeconds(Duration.ofDays(2).getSeconds());

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.WAIT_MATERIAL_PURCHASE);
        // Nhập trung bình 4 ngày + sản xuất 2 ngày.
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(6)));
        assertThat(line.estimatedWaitDays()).isEqualTo(6);
        assertThat(line.depositRecommended()).isTrue();
        assertThat(line.materials().get(0).arrivalSource()).isEqualTo("RECEIPT_HISTORY");
        assertThat(line.message()).contains("Trà xanh");
    }

    @Test
    void materialWithoutHistory_usesAverageOfOtherMaterials() {
        stock("0", "0");
        materialStock("0");
        when(queries.averageSupplyLeadSeconds(InventoryItemType.INGREDIENT))
                .thenReturn((double) Duration.ofDays(5).getSeconds());

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        // Nhập trung bình 5 ngày + sản xuất mặc định 1 ngày.
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(6)));
        assertThat(line.materials().get(0).arrivalSource()).isEqualTo("RECEIPT_HISTORY");
    }

    @Test
    void noReceiptAndNoHistory_usesDefaults() {
        stock("0", "0");
        materialStock("0");

        LineEstimate line = service.estimate(request("10")).lines().get(0);

        assertThat(line.scenario()).isEqualTo(AvailabilityScenario.WAIT_MATERIAL_PURCHASE);
        // Mặc định: nhập 3 ngày + sản xuất 1 ngày.
        assertThat(line.estimatedAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(4)));
        assertThat(line.materials().get(0).arrivalSource()).isEqualTo("DEFAULT");
    }

    @Test
    void partOfMissingCoveredByRunningOrder_restProducedFromMaterials() {
        stock("0", "0");
        ProductionOrder running = production("LSX-004", "4", ProductionOrderStatus.PENDING_APPROVAL, NOW, null);
        when(queries.findOpenProductionOrders(eq(variantId), anyCollection())).thenReturn(List.of(running));
        materialStock("0.6"); // còn phải làm 6 hộp → cần 0,6

        List<AvailabilityEstimateService.LinePlan> plans = service.plan(request("10"));

        assertThat(plans.get(0).estimate().scenario()).isEqualTo(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS);
        assertThat(plans.get(0).quantityToProduce()).isEqualByComparingTo("6");
        assertThat(plans.get(0).estimate().materials().get(0).requiredQuantity()).isEqualByComparingTo("0.6");
    }

    @Test
    void noBom_cannotEstimate() {
        stock("0", "0");
        when(catalogClient.getBomItems(variantId)).thenReturn(List.of());

        AvailabilityEstimateResponse response = service.estimate(request("10"));

        assertThat(response.lines().get(0).scenario()).isEqualTo(AvailabilityScenario.NOT_PRODUCIBLE);
        assertThat(response.lines().get(0).estimatedAvailableAt()).isNull();
        assertThat(response.estimatedAvailableAt()).isNull();
        assertThat(response.backorder()).isTrue();
    }

    @Test
    void waitDays_roundsUpPartialDays() {
        assertThat(AvailabilityEstimateService.waitDays(NOW, NOW)).isZero();
        assertThat(AvailabilityEstimateService.waitDays(NOW, NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(AvailabilityEstimateService.waitDays(NOW, NOW.plus(Duration.ofHours(24)))).isEqualTo(1);
        assertThat(AvailabilityEstimateService.waitDays(NOW, NOW.plus(Duration.ofHours(25)))).isEqualTo(2);
    }

    // ------------------------------------------------------------------ fixtures

    private AvailabilityEstimateRequest request(String quantity) {
        return new AvailabilityEstimateRequest(null, null, null,
                List.of(new AvailabilityEstimateRequest.Line(variantId, new BigDecimal(quantity))));
    }

    private void stock(String shelf, String warehouse) {
        BigDecimal shelfQuantity = new BigDecimal(shelf);
        BigDecimal warehouseQuantity = new BigDecimal(warehouse);
        when(stockService.getStocksByVariantIds(any())).thenReturn(List.of(new StockVariantBatchResponse(
                variantId, null, shelfQuantity.add(warehouseQuantity), BigDecimal.ZERO, "IN_STOCK",
                shelfQuantity, warehouseQuantity, null, null)));
    }

    private void materialStock(String quantity) {
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(material);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        when(balanceRepository.findSellableBatchBalancesByInventoryItemIds(any(), any(), any(), any()))
                .thenReturn(List.of(balance));
    }

    private InventoryItem item(UUID referenceId, String name, InventoryItemType type) {
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setReferenceId(referenceId);
        item.setName(name);
        item.setItemType(type);
        return item;
    }

    private ProductionOrder production(String code, String planned, ProductionOrderStatus status, Instant createdAt,
                                       UUID sourceOrderId) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderCode(code);
        order.setProductVariant(product);
        order.setPlannedQuantity(new BigDecimal(planned));
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        order.setSourceOrderId(sourceOrderId);
        return order;
    }

    private InventoryDocumentLine receiptLine(String code, InventoryDocumentStatus status, String ordered,
                                              Instant expectedDate, Instant createdAt) {
        InventoryDocument document = new GoodsReceipt();
        document.setDocumentCode(code);
        document.setStatus(status);
        document.setExpectedDate(expectedDate);
        document.setCreatedAt(createdAt);
        InventoryDocumentLine line = new InventoryDocumentLine();
        line.setDocument(document);
        line.setInventoryItem(material);
        line.setOrderedQuantity(new BigDecimal(ordered));
        return line;
    }
}
