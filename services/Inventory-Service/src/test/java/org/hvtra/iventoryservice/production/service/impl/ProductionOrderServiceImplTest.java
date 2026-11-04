package org.hvtra.iventoryservice.production.service.impl;

import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.mapper.ProductionOrderMapper;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionOrderServiceImplTest {

    @Mock
    private ProductionOrderRepository productionOrderRepository;

    @Mock
    private BusinessSequenceService sequenceService;

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryLocationRepository locationRepository;

    @Mock
    private InventoryBatchRepository batchRepository;

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private CatalogClient catalogClient;

    @Spy
    private ProductionOrderMapper mapper = new ProductionOrderMapper();

    @Mock
    private InventoryReservationService reservationService;

    @Mock
    private SupplyLeadTimeService supplyLeadTimeService;

    @InjectMocks
    private ProductionOrderServiceImpl service;

    private UUID orderId;
    private ProductionOrder order;
    private InventoryItem productVariant;
    private InventoryItem ingredient;

    @BeforeEach
    void setUp() {
        orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

        productVariant = new InventoryItem();
        productVariant.setId(UUID.randomUUID());
        productVariant.setReferenceCode("TRA-HOP-001");
        productVariant.setName("Trà Hộp 100g");

        ingredient = new InventoryItem();
        ingredient.setId(UUID.randomUUID());
        ingredient.setReferenceCode("NL-0001");
        ingredient.setName("Búp Trà Xanh");

        order = new ProductionOrder();
        order.setId(orderId);
        order.setOrderCode("LSX-202611-0001");
        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setProductVariant(productVariant);
        order.setPlannedQuantity(new BigDecimal("50.00"));
        order.setCreatedAt(Instant.now());

        ProductionOrderLine line = new ProductionOrderLine();
        line.setId(UUID.randomUUID());
        line.setIngredient(ingredient);
        line.setRequiredQuantity(new BigDecimal("10.00"));
        order.setLines(new ArrayList<>(List.of(line)));
    }

    @Test
    @DisplayName("findAll - Success returning paged production orders")
    void findAll_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductionOrder> page = new PageImpl<>(List.of(order), pageable, 1);
        when(productionOrderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<ProductionOrderResponse> response = service.findAll(
                ProductionOrderStatus.DRAFT, null, "LSX", pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().orderCode()).isEqualTo("LSX-202611-0001");
    }

    @Test
    @DisplayName("findById - Success when order exists")
    void findById_Success() {
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        ProductionOrderResponse response = service.findById(orderId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("LSX-202611-0001");
        assertThat(response.status()).isEqualTo(ProductionOrderStatus.DRAFT);
    }

    @Test
    @DisplayName("findById - Throws ResourceNotFoundException when id not found")
    void findById_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(productionOrderRepository.findByIdWithLines(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ProductionOrder");
    }

    @Test
    @DisplayName("submit - Success transitioning DRAFT to PENDING_APPROVAL")
    void submit_Success() {
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.submit(orderId);

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.PENDING_APPROVAL);
        verify(productionOrderRepository).save(order);
    }

    @Test
    @DisplayName("submit - Throws BadRequestException when not DRAFT")
    void submit_NotDraft_ThrowsException() {
        order.setStatus(ProductionOrderStatus.APPROVED);
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.submit(orderId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lệnh sản xuất phải ở trạng thái");
    }

    @Test
    @DisplayName("submit - Throws BadRequestException when lines is empty")
    void submit_EmptyLines_ThrowsException() {
        order.setLines(new ArrayList<>());
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.submit(orderId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lệnh sản xuất chưa có dòng nguyên liệu nào");
    }

    @Test
    @DisplayName("reject - Success rejecting order in PENDING_APPROVAL status")
    void reject_Success() {
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.reject(orderId);

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.REJECTED);
        verify(productionOrderRepository).save(order);
    }

    @Test
    @DisplayName("reject - Throws BadRequestException when not PENDING_APPROVAL")
    void reject_NotPendingApproval_ThrowsException() {
        order.setStatus(ProductionOrderStatus.DRAFT);
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reject(orderId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lệnh sản xuất phải ở trạng thái");
    }
}
