package org.hvtra.iventoryservice.production.service.impl;

import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.mapper.ProductionOrderMapper;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        productVariant.setReferenceId(UUID.randomUUID());
        productVariant.setReferenceCode("TRA-HOP-001");
        productVariant.setName("Trà Hộp 100g");

        ingredient = new InventoryItem();
        ingredient.setId(UUID.randomUUID());
        ingredient.setReferenceId(UUID.randomUUID());
        ingredient.setReferenceCode("NL-0001");
        ingredient.setName("Búp Trà Xanh");

        order = new ProductionOrder();
        order.setId(orderId);
        order.setOrderCode("LSX-202611-0001");
        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setProductVariant(productVariant);
        order.setPlannedQuantity(new BigDecimal("50.00"));
        order.setLocationId(UUID.randomUUID());
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
        authenticateWith(Role.MANAGER);
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
        assertThat(response.productVariantName()).isEqualTo("Trà Hộp 100g");
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).ingredientName()).isEqualTo("Búp Trà Xanh");
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
    @DisplayName("submit - Success transitioning DRAFT to PENDING_APPROVAL and reserving materials")
    void submit_Success() {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(UUID.randomUUID());
        batch.setInventoryItem(ingredient);

        InventoryLocation loc = new InventoryLocation();
        loc.setId(UUID.randomUUID());

        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryBatch(batch);
        balance.setLocation(loc);
        balance.setQuantityOnHand(new BigDecimal("20.00"));
        balance.setQuantityReserved(BigDecimal.ZERO);
        balance.setQuantityBlocked(BigDecimal.ZERO);

        when(batchRepository.findFefoCandidates(eq(ingredient.getId()), eq(InventoryBatchStatus.AVAILABLE)))
                .thenReturn(List.of(batch));
        when(balanceRepository.findForUpdateByBatchId(batch.getId())).thenReturn(List.of(balance));
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.submit(orderId);

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.PENDING_APPROVAL);
        assertThat(order.getLines().getFirst().getReservedQuantity()).isEqualByComparingTo("10.00");
        assertThat(balance.getQuantityReserved()).isEqualByComparingTo("10.00");
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(Role role) {
        authenticateWith(role, UUID.randomUUID());
    }

    private void authenticateWith(Role role, UUID userId) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                userId.toString(),
                "test-user",
                "test-issuer",
                Set.of(role.name()),
                "corr-123"
        );
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @Test
    @DisplayName("reject - Success rejecting order in PENDING_APPROVAL status by Manager")
    void reject_Success() {
        authenticateWith(Role.MANAGER);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.reject(orderId);

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.REJECTED);
        verify(productionOrderRepository).save(order);
    }

    @Test
    @DisplayName("reject - Throws BadRequestException when not PENDING_APPROVAL by Manager")
    void reject_NotPendingApproval_ThrowsException() {
        authenticateWith(Role.MANAGER);
        order.setStatus(ProductionOrderStatus.DRAFT);
        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reject(orderId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lệnh sản xuất phải ở trạng thái");
    }

    @Test
    @DisplayName("reject - Throws AccessDeniedException when caller is Inventory Staff")
    void reject_ThrowsAccessDenied_WhenInventoryStaff() {
        authenticateWith(Role.INVENTORY_STAFF);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.reject(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Quản lý hoặc Admin mới có quyền");
    }

    @Test
    @DisplayName("approve - Throws AccessDeniedException when caller is Inventory Staff")
    void approve_ThrowsAccessDenied_WhenInventoryStaff() {
        authenticateWith(Role.INVENTORY_STAFF);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.approve(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Quản lý hoặc Admin mới có quyền");
    }

    @Test
    @DisplayName("cancel - Success cancelling PENDING_APPROVAL order by creator")
    void cancel_Success_WhenPendingApproval_ByCreator() {
        UUID creatorId = UUID.randomUUID();
        order.setCreatedBy(creatorId);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        authenticateWith(Role.INVENTORY_STAFF, creatorId);

        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.cancel(orderId, new CancelProductionOrderRequest("Thay đổi kế hoạch sản xuất"));

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo(creatorId);
        assertThat(order.getCancelReason()).isEqualTo("Thay đổi kế hoạch sản xuất");
        verify(productionOrderRepository).save(order);
    }

    @Test
    @DisplayName("cancel - Throws BadRequestException when caller is not the creator")
    void cancel_ThrowsBadRequest_WhenNotCreator() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        order.setCreatedBy(creatorId);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        authenticateWith(Role.INVENTORY_STAFF, otherUserId);

        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(orderId, new CancelProductionOrderRequest("Lý do khác")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ người tạo mới có quyền hủy lệnh sản xuất");
    }

    @Test
    @DisplayName("cancel - Success cancelling PENDING_APPROVAL order by creator who is Manager")
    void cancel_Success_WhenPendingApproval_ByCreatorManager() {
        UUID managerId = UUID.randomUUID();
        order.setCreatedBy(managerId);
        order.setStatus(ProductionOrderStatus.PENDING_APPROVAL);
        authenticateWith(Role.MANAGER, managerId);

        when(productionOrderRepository.findByIdWithLines(orderId)).thenReturn(Optional.of(order));
        when(productionOrderRepository.save(any(ProductionOrder.class))).thenReturn(order);

        ProductionOrderResponse response = service.cancel(orderId, new CancelProductionOrderRequest("Hủy bởi Quản lý"));

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo(managerId);
        verify(productionOrderRepository).save(order);
    }
}
