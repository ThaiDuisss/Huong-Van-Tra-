package org.hvtra.iventoryservice.availability.service;

import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.availability.AvailabilityScenario;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateRequest;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.LineEstimate;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService.LinePlan;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService.MaterialNeed;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.production.service.ProductionOrderService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackorderPreparationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");

    @Mock
    private AvailabilityEstimateService estimateService;
    @Mock
    private AvailabilityQueryRepository queries;
    @Mock
    private ProductionOrderService productionOrderService;
    @Mock
    private ProductionOrderRepository productionOrderRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryReplenishmentAlertRepository alertRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BackorderPreparationService service;
    private UUID orderId;
    private UUID variantId;
    private InventoryLocation warehouse;
    private ProductionOrder createdEntity;
    private UUID createdId;

    @BeforeEach
    void setUp() {
        service = new BackorderPreparationService(estimateService, queries, productionOrderService,
                productionOrderRepository, locationRepository, alertRepository, transactionManager);
        orderId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        warehouse = new InventoryLocation();
        warehouse.setId(UUID.randomUUID());
        when(locationRepository.findFirstByTypeAndStatusOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.of(warehouse));
        when(queries.findBySourceOrderAndVariant(orderId, variantId)).thenReturn(List.of());

        createdId = UUID.randomUUID();
        createdEntity = new ProductionOrder();
        createdEntity.setId(createdId);
        when(productionOrderService.create(any())).thenReturn(response(createdId, "LSX-NEW"));
        when(productionOrderRepository.findById(createdId)).thenReturn(Optional.of(createdEntity));
        when(estimateService.toResponse(any())).thenAnswer(invocation -> {
            List<LinePlan> plans = invocation.getArgument(0);
            return new AvailabilityEstimateResponse(true, null, plans.stream().map(LinePlan::estimate).toList());
        });
    }

    @Test
    void materialsInStock_createsProductionOrderLinkedToOrderAndSubmitsIt() {
        when(estimateService.plan(any())).thenReturn(List.of(
                plan(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS, "2.345", List.of())));

        AvailabilityEstimateResponse response = service.prepare(request());

        ArgumentCaptor<CreateProductionOrderRequest> captor = ArgumentCaptor.forClass(CreateProductionOrderRequest.class);
        verify(productionOrderService).create(captor.capture());
        assertThat(captor.getValue().productVariantId()).isEqualTo(variantId);
        assertThat(captor.getValue().plannedQuantity()).isEqualByComparingTo("2.35");
        assertThat(captor.getValue().locationId()).isEqualTo(warehouse.getId());
        assertThat(captor.getValue().note()).contains("ORD-1");
        assertThat(createdEntity.getSourceOrderId()).isEqualTo(orderId);
        assertThat(createdEntity.getSourceOrderCode()).isEqualTo("ORD-1");
        verify(productionOrderService).submit(createdId);
        verify(alertRepository, never()).save(any());
        assertThat(response.lines().get(0).productionOrderCode()).isEqualTo("LSX-NEW");
    }

    @Test
    void materialsMissingWithoutReceipt_createsDraftOrderAndReorderAlert() {
        InventoryItem material = new InventoryItem();
        material.setId(UUID.randomUUID());
        MaterialNeed need = new MaterialNeed(material, new MaterialEstimate(UUID.randomUUID(), "Trà xanh",
                new BigDecimal("1.0"), new BigDecimal("0.4"), new BigDecimal("0.6"), NOW, "RECEIPT_HISTORY", null));
        when(estimateService.plan(any())).thenReturn(List.of(
                plan(AvailabilityScenario.WAIT_MATERIAL_PURCHASE, "10", List.of(need))));
        when(alertRepository.existsOpenAlert(any(), any(), any())).thenReturn(false);

        service.prepare(request());

        verify(productionOrderService).create(any());
        verify(productionOrderService, never()).submit(any());
        ArgumentCaptor<InventoryReplenishmentAlert> alert = ArgumentCaptor.forClass(InventoryReplenishmentAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getAlertType()).isEqualTo(InventoryReplenishmentAlertType.REORDER_REQUIRED);
        assertThat(alert.getValue().getSuggestedQuantity()).isEqualByComparingTo("0.6");
        assertThat(alert.getValue().getInventoryItem()).isSameAs(material);
    }

    @Test
    void materialCoveredByOpenReceipt_doesNotOpenAlert() {
        InventoryItem material = new InventoryItem();
        material.setId(UUID.randomUUID());
        MaterialNeed need = new MaterialNeed(material, new MaterialEstimate(UUID.randomUUID(), "Trà xanh",
                new BigDecimal("1.0"), BigDecimal.ZERO, new BigDecimal("1.0"), NOW, "OPEN_RECEIPT", "PN-001"));
        when(estimateService.plan(any())).thenReturn(List.of(
                plan(AvailabilityScenario.WAIT_MATERIAL_RECEIPT, "10", List.of(need))));

        service.prepare(request());

        verify(productionOrderService).create(any());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void calledAgainForSameOrder_doesNotCreateSecondProductionOrder() {
        ProductionOrder linked = new ProductionOrder();
        linked.setOrderCode("LSX-OLD");
        when(queries.findBySourceOrderAndVariant(orderId, variantId)).thenReturn(List.of(linked));
        when(estimateService.plan(any())).thenReturn(List.of(
                plan(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS, "5", List.of())));

        AvailabilityEstimateResponse response = service.prepare(request());

        verify(productionOrderService, never()).create(any());
        assertThat(response.lines().get(0).productionOrderCode()).isEqualTo("LSX-OLD");
    }

    @Test
    void runningOrderAlreadyCoversMissing_createsNothing() {
        when(estimateService.plan(any())).thenReturn(List.of(plan(AvailabilityScenario.IN_PRODUCTION, "0", List.of())));

        service.prepare(request());

        verify(productionOrderService, never()).create(any());
    }

    @Test
    void oneLineFailing_doesNotBreakTheResponse() {
        when(estimateService.plan(any())).thenReturn(List.of(
                plan(AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS, "5", List.of())));
        when(productionOrderService.create(any())).thenThrow(new BadRequestException("missing inventory item"));

        AvailabilityEstimateResponse response = service.prepare(request());

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).productionOrderCode()).isNull();
    }

    @Test
    void orderIdIsRequired() {
        assertThatThrownBy(() -> service.prepare(new AvailabilityEstimateRequest(null, null, null,
                List.of(new AvailabilityEstimateRequest.Line(variantId, BigDecimal.ONE)))))
                .isInstanceOf(BadRequestException.class);
    }

    private AvailabilityEstimateRequest request() {
        return new AvailabilityEstimateRequest(null, orderId, "ORD-1",
                List.of(new AvailabilityEstimateRequest.Line(variantId, BigDecimal.TEN)));
    }

    private LinePlan plan(AvailabilityScenario scenario, String quantityToProduce, List<MaterialNeed> needs) {
        LineEstimate estimate = new LineEstimate(variantId, "Trà sen", BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.TEN, scenario, NOW, 0, BigDecimal.ONE, "DEFAULT", null, false,
                needs.stream().map(MaterialNeed::estimate).toList(), "");
        return new LinePlan(estimate, List.of(), new BigDecimal(quantityToProduce), needs);
    }

    private ProductionOrderResponse response(UUID id, String code) {
        return new ProductionOrderResponse(id, code, ProductionOrderStatus.DRAFT, variantId, BigDecimal.TEN, null,
                warehouse.getId(), null, null, null, null, List.of(), null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
