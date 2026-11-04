package org.hvtra.iventoryservice.customerreturn;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnInventoryCommandResponse;

import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.PostCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.impl.CustomerReturnInspectionServiceImpl;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.disposal.service.impl.DisposalRequestServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnCondition;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnItemResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.outbox.publisher.InventoryOutboxPublisher;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomerReturnInspectionServiceImplTest {

    @Mock
    private CustomerReturnInspectionRepository inspectionRepository;

    @Mock
    private OrderReturnClient orderReturnClient;

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
    private DisposalRequestRepository disposalRequestRepository;
    @Mock
    private InventoryInboxRepository inboxRepository;
    @Mock
    private InventoryOutboxPublisher inventoryOutboxPublisher;
    @Mock private EntityManager entityManager;

    private CustomerReturnInspectionServiceImpl service;
    private UUID returnId;
    private UUID returnItemId;
    private UUID orderItemId;
    private UUID productVariantId;
    private UUID branchId;
    private InventoryItem item;
    private InventoryBatch batch;
    private InventoryLocation shelf;
    private InventoryLocation quarantine;
    private InventoryLocation damaged;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        authenticate("QC");
        service = new CustomerReturnInspectionServiceImpl(
                inspectionRepository,
                orderReturnClient,
                itemRepository,
                locationRepository,
                batchRepository,
                balanceRepository,
                transactionRepository,
                disposalRequestRepository,
                inboxRepository,
                inventoryOutboxPublisher,
                entityManager);
        returnId = UUID.randomUUID();
        returnItemId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
        productVariantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        item = inventoryItem();
        batch = batch(item);
        shelf = location(InventoryLocationType.SHELF, branchId);
        quarantine = location(InventoryLocationType.QUARANTINE, branchId);
        damaged = location(InventoryLocationType.DAMAGED, branchId);
        lenient().when(itemRepository.findByReferenceIdAndItemTypeForUpdate(
                any(), eq(InventoryItemType.PRODUCT_VARIANT))).thenReturn(Optional.of(item));

        lenient().when(inspectionRepository.existsByInspectionCode(anyString())).thenReturn(false);
        lenient().when(inspectionRepository.saveAndFlush(any(CustomerReturnInspection.class)))
                .thenAnswer(invocation -> assignInspectionIds(invocation.getArgument(0)));
        lenient().when(transactionRepository.saveAndFlush(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> assignTransactionId(invocation.getArgument(0)));
        lenient().when(disposalRequestRepository.saveAndFlush(any(DisposalRequest.class)))
                .thenAnswer(invocation -> {
                    DisposalRequest request = invocation.getArgument(0);
                    if (request.getId() == null) {
                        request.setId(UUID.randomUUID());
                    }
                    return request;
                });
        lenient().when(inspectionRepository.findByReturnIdAndStatus(
                any(),
                eq(CustomerReturnInspectionStatus.POSTED)))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                actorId.toString(), "qc-test", "test", Set.of(role), "test-request");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void createInspectionRejectsCreatedReturn() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.CREATED));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "khach tra unopened")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only RECEIVED");
    }

    @Test
    void createInspectionRejectsCancelledReturn() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.CANCELLED));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "khach tra unopened")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only RECEIVED");
    }

    @Test
    void createInspectionRejectsCompletedReturn() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.COMPLETED));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "khach tra unopened")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only RECEIVED");
    }

    @Test
    void createInspectionAllowsSealedRestock() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.RESTOCK,
                shelf.getId(),
                null));

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.CREATED);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.RESTOCK);
    }

    @Test
    void createInspectionAllowsSealedWriteOffWithoutNote() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                null));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
    }

    @Test
    void createInspectionAllowsSealedWriteOffWithNote() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "seal bi moc"));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
    }

    @Test
    void createInspectionRejectsOpenedActualRestockEvenWhenDeclaredSealed() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        assertThatThrownBy(() -> service.createInspection(returnId, actualRequest(
                CustomerReturnActualCondition.OPENED, CustomerReturnQcResult.RESTOCK, shelf.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESTOCK is not allowed for QC actual condition OPENED");
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInspectionRejectsQuarantineWithDestination() {
        stubReceivedReturn(OrderReturnCondition.OPENED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                quarantine.getId(),
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("QUARANTINE is no longer supported");

        verify(inspectionRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInspectionAllowsSpoiledWriteOff() {
        stubReceivedReturn(OrderReturnCondition.SPOILED, "3.00");
        stubActiveItem();

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                null));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
    }

    @Test
    void createInspectionAllowsDamagedDisposition() {
        stubReceivedReturn(OrderReturnCondition.DAMAGED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(damaged.getId())).thenReturn(Optional.of(damaged));

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.DAMAGED,
                damaged.getId(),
                null));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.DAMAGED);
    }

    @Test
    void createInspectionRejectsNonPositiveQuantity() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "0.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "note enough")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than 0");
    }

    @Test
    void createInspectionRejectsQuantityOverReturnedQuantity() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "4.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "note enough")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds returned quantity");
    }

    @Test
    void createInspectionRejectsQuantityOverRemainingAfterPostedQc() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        when(inspectionRepository.findByReturnIdForUpdate(returnId))
                .thenReturn(List.of(postedInspection("2.00")));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "2.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "note enough")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds returned quantity");
    }

    @Test
    void createInspectionRejectsUnknownReturnItem() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                UUID.randomUUID(),
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "note enough")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createInspectionRejectsDuplicateReturnItemInRequest() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();
        CreateCustomerReturnInspectionRequest request = new CreateCustomerReturnInspectionRequest(
                null,
                List.of(
                        requestLine(returnItemId, "1.00", CustomerReturnQcResult.WRITE_OFF, null, "note enough"),
                        requestLine(returnItemId, "1.00", CustomerReturnQcResult.WRITE_OFF, null, "note enough")));

        assertThatThrownBy(() -> service.createInspection(returnId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate returnItemId");
    }

    @Test
    void createInspectionRejectsMissingInventoryItemMapping() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        when(itemRepository.findByReferenceIdAndItemType(productVariantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.WRITE_OFF,
                null,
                "note enough")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory item for product variant");
    }

    @Test
    void createInspectionRejectsRestockWithoutDestination() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.RESTOCK,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destinationLocationId is required");
    }

    @Test
    void createInspectionRejectsRestockToDamagedLocation() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(damaged.getId())).thenReturn(Optional.of(damaged));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.RESTOCK,
                damaged.getId(),
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("saleable");
    }

    @Test
    void createInspectionRejectsQuarantineWithoutDestination() {
        stubReceivedReturn(OrderReturnCondition.DAMAGED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("QUARANTINE is no longer supported");
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInspectionRejectsDamagedToSaleableLocation() {
        stubReceivedReturn(OrderReturnCondition.DAMAGED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.DAMAGED,
                shelf.getId(),
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DAMAGED location");
    }

    @Test
    void postRestockIncreasesOnHandAndCreatesCustomerReturnReceipt() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        InventoryBalance balance = balance("5.00", "0.00", "0.00", shelf);
        stubPost(inspection, balance);

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("6.00");
        assertThat(balance.getQuantityBlocked()).isEqualByComparingTo("0.00");
        assertThat(inspection.getLines().getFirst().getPosted()).isTrue();
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(InventoryTransactionType.CUSTOMER_RETURN_RECEIPT);
        assertThat(captor.getValue().getLines()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = CustomerReturnQcResult.class, names = {"DAMAGED", "QUARANTINE"})
    void postDamagedAndLegacyQuarantineIncreaseOnHandAndBlocked(CustomerReturnQcResult result) {
        InventoryLocation destination = result == CustomerReturnQcResult.DAMAGED ? damaged : quarantine;
        CustomerReturnInspection inspection = inspection(result, destination, "1.00");
        InventoryBalance balance = balance("5.00", "0.00", "2.00", destination);
        stubPost(inspection, balance);

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.lines().getFirst().result()).isEqualTo(result);
        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("6.00");
        assertThat(balance.getQuantityBlocked()).isEqualByComparingTo("3.00");
        verify(transactionRepository).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QC", "MANAGER", "SUPER_ADMIN"})
    void postWriteOffDirectlyWithoutApprovalOrBalanceChange(String role) {
        authenticate(role);
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        stubWriteOffPost(inspection);
        InventoryInbox inbox = attachInbox(inspection);

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        assertThat(inspection.getPostedAt()).isNotNull();
        assertThat(response.lines().getFirst().posted()).isTrue();
        assertThat(response.lines().getFirst().disposalRequestId()).isNull();
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        InventoryTransaction transaction = captor.getValue();
        assertThat(transaction.getType()).isEqualTo(InventoryTransactionType.WRITE_OFF);
        assertThat(transaction.getReferenceId()).isEqualTo(returnId.toString());
        assertThat(transaction.getLines()).hasSize(1);
        assertThat(transaction.getLines().getFirst().getQuantity()).isEqualByComparingTo("-1.00");
        assertThat(transaction.getLines().getFirst().getLocation()).isNull();
        assertThat(transaction.getLines().getFirst().getBeforeQuantity()).isZero();
        assertThat(transaction.getLines().getFirst().getAfterQuantity()).isZero();
        assertThat(response.lines().getFirst().transactionId()).isEqualTo(transaction.getId());
        verify(disposalRequestRepository, never()).saveAndFlush(any());
        verifyNoInteractions(balanceRepository, batchRepository);
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        verify(inventoryOutboxPublisher).publishReturnInventoryDispositionProcessed(
                inbox.getCommandId(), returnId.toString(), inspection.getId().toString(),
                inbox.getSagaId(), inbox.getCorrelationId());
    }

    @Test
    void createWriteOffDoesNotCreatePendingDisposalRequest() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();
        CustomerReturnInspectionResponse response = service.createInspection(returnId,
                request(returnItemId, "1.00", CustomerReturnQcResult.WRITE_OFF, null, null));
        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.CREATED);
        assertThat(response.lines().getFirst().disposalStatus()).isNull();
        verifyNoInteractions(disposalRequestRepository, transactionRepository, balanceRepository);
    }

    @Test
    void postLegacyPendingDisposalCompletesOnQcPostAndPreservesReference() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        inspection.setStatus(CustomerReturnInspectionStatus.PROCESSING);
        stubWriteOffPost(inspection);
        DisposalRequest legacy = new DisposalRequest();
        legacy.setId(UUID.randomUUID());
        legacy.setSourceType("CUSTOMER_RETURN_INSPECTION");
        legacy.setSourceInspection(inspection);
        legacy.setStatus(DisposalRequestStatus.PENDING_APPROVAL);
        legacy.addLine(inspection.getLines().getFirst());
        when(disposalRequestRepository.findBySourceInspectionId(inspection.getId())).thenReturn(Optional.of(legacy));
        when(disposalRequestRepository.findDetailByIdForUpdate(legacy.getId())).thenReturn(Optional.of(legacy));

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        assertThat(legacy.getStatus()).isEqualTo(DisposalRequestStatus.APPROVED);
        assertThat(legacy.getApprovedBy()).isEqualTo(actorId);
        assertThat(legacy.getApprovedAt()).isNotNull();
        assertThat(legacy.getTransactionId()).isEqualTo(response.lines().getFirst().transactionId());
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReferenceId()).isEqualTo(legacy.getId().toString());
        verify(disposalRequestRepository).saveAndFlush(legacy);
        verifyNoInteractions(balanceRepository);

        authenticate("SUPER_ADMIN");
        when(disposalRequestRepository.findDetailById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(inspectionRepository.findByIdAndIsDeletedFalseForUpdate(inspection.getId()))
                .thenReturn(Optional.of(inspection));
        DisposalRequestServiceImpl legacyApproval = new DisposalRequestServiceImpl(
                disposalRequestRepository, inspectionRepository, transactionRepository, service);
        legacyApproval.approveRequest(legacy.getId());
        verify(transactionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void postWriteOffRetryDoesNotDuplicateMovement() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        stubWriteOffPost(inspection);
        attachInbox(inspection);

        service.postInspection(inspection.getId());
        service.postInspection(inspection.getId());

        verify(transactionRepository, times(1)).saveAndFlush(any());
        verify(disposalRequestRepository, never()).saveAndFlush(any());
        verify(inboxRepository, times(1)).saveAndFlush(any());
        verifyNoInteractions(balanceRepository);
    }

    @Test
    void postWriteOffPreservesPartialQuantityAndCompletesFullQuantity() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        stubWriteOffPost(inspection);
        service.postInspection(inspection.getId());
        when(inspectionRepository.findQcProgressByReturnId(returnId)).thenReturn(List.of(inspection));
        CustomerReturnQcSummaryResponse partial = service.getQcSummary(returnId);
        assertThat(partial.qcStatus()).isEqualTo(CustomerReturnQcStatus.PARTIAL);
        assertThat(partial.lines().getFirst().postedQcQuantity()).isEqualByComparingTo("1.00");
        assertThat(partial.lines().getFirst().remainingQcQuantity()).isEqualByComparingTo("2.00");

        CustomerReturnInspection remaining = inspection(CustomerReturnQcResult.WRITE_OFF, null, "2.00");
        when(inspectionRepository.findByIdAndIsDeletedFalse(remaining.getId())).thenReturn(Optional.of(remaining));
        when(inspectionRepository.findByReturnIdForUpdate(returnId)).thenReturn(List.of(inspection, remaining));
        service.postInspection(remaining.getId());
        when(inspectionRepository.findQcProgressByReturnId(returnId)).thenReturn(List.of(inspection, remaining));
        CustomerReturnQcSummaryResponse completed = service.getQcSummary(returnId);
        assertThat(completed.qcStatus()).isEqualTo(CustomerReturnQcStatus.COMPLETED);
        assertThat(completed.lines().getFirst().postedQcQuantity()).isEqualByComparingTo("3.00");
        assertThat(completed.lines().getFirst().remainingQcQuantity()).isZero();
        verify(transactionRepository, times(2)).saveAndFlush(any());
        verifyNoInteractions(balanceRepository);
    }

    @Test
    void postWriteOffRejectsInactiveItemBeforeMovement() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        stubWriteOffPost(inspection);
        item.setStatus(InventoryStatus.INACTIVE);
        assertThatThrownBy(() -> service.postInspection(inspection.getId()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("active mapped inventory item");
        verifyNoInteractions(transactionRepository, disposalRequestRepository, balanceRepository);
    }

    @Test
    void postMultipleWriteOffLinesUsesOneNegativeTransaction() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        CustomerReturnInspectionLine second = inspection(CustomerReturnQcResult.WRITE_OFF, null, "2.00")
                .getLines().getFirst();
        second.setReturnItemId(UUID.randomUUID());
        inspection.addLine(second);
        stubWriteOffPost(inspection);
        stubSecondReturnItem(second);

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(2);
        assertThat(captor.getValue().getLines().stream().map(line -> line.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("-3.00");
        assertThat(response.lines()).allSatisfy(line -> {
            assertThat(line.posted()).isTrue();
            assertThat(line.transactionId()).isEqualTo(captor.getValue().getId());
        });
        verifyNoInteractions(balanceRepository, batchRepository);
    }

    @Test
    void postMixedRestockAndWriteOffCompletesBothBeforeAcknowledging() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        CustomerReturnInspectionLine writeOff = inspection(CustomerReturnQcResult.WRITE_OFF, null, "2.00")
                .getLines().getFirst();
        writeOff.setReturnItemId(UUID.randomUUID());
        inspection.addLine(writeOff);
        InventoryBalance balance = balance("5.00", "0.00", "0.00", shelf);
        stubPost(inspection, balance);
        stubSecondReturnItem(writeOff);
        InventoryInbox inbox = attachInbox(inspection);

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("6.00");
        assertThat(balance.getQuantityBlocked()).isZero();
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(InventoryTransaction::getType)
                .containsExactly(InventoryTransactionType.CUSTOMER_RETURN_RECEIPT, InventoryTransactionType.WRITE_OFF);
        assertThat(captor.getAllValues().getLast().getLines().getFirst().getQuantity()).isEqualByComparingTo("-2.00");
        assertThat(writeOff.getPosted()).isTrue();
        verify(balanceRepository, times(1)).save(any());
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        verify(inventoryOutboxPublisher).publishReturnInventoryDispositionProcessed(
                inbox.getCommandId(), returnId.toString(), inspection.getId().toString(),
                inbox.getSagaId(), inbox.getCorrelationId());
    }

    private void stubSecondReturnItem(CustomerReturnInspectionLine line) {
        OrderReturnResponse original = orderReturn(OrderReturnStatus.RECEIVED);
        OrderReturnItemResponse second = new OrderReturnItemResponse(
                line.getReturnItemId(), line.getOrderItemId(), productVariantId, new BigDecimal("2.00"),
                "DEFECTIVE", OrderReturnCondition.SEALED, null, "SKU-001", "Tra sen", "100g");
        when(orderReturnClient.getReturnById(returnId)).thenReturn(new OrderReturnResponse(
                original.id(), original.returnCode(), original.orderId(), original.orderCode(), original.branchId(),
                original.status(), original.note(), original.createdAt(), original.updatedAt(),
                List.of(original.items().getFirst(), second)));
    }

    @Test
    void postInspectionRejectsInventoryStaffBeforeAnyMutation() {
        authenticate("INVENTORY_STAFF");
        assertThatThrownBy(() -> service.postInspection(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(inspectionRepository, transactionRepository, disposalRequestRepository, balanceRepository);
    }

    @Test
    void createInspectionRejectsNonQcRoleBeforeAnyMutation() {
        authenticate("SALE_STAFF");
        assertThatThrownBy(() -> service.createInspection(returnId,
                request(returnItemId, "1.00", CustomerReturnQcResult.WRITE_OFF, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(inspectionRepository, orderReturnClient, transactionRepository, disposalRequestRepository);
    }

    @ParameterizedTest
    @EnumSource(value = CustomerReturnQcResult.class, names = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
    void distinctCommandsWithIdenticalLinesPostThreePartialsAndRetriesDoNotMoveAgain(CustomerReturnQcResult result) {
        InventoryLocation destination = result == CustomerReturnQcResult.WRITE_OFF ? null
                : result == CustomerReturnQcResult.DAMAGED ? damaged : shelf;
        InventoryBalance stock = balance("10.00", "0.00", "0.00", destination == null ? shelf : destination);
        List<CustomerReturnInspection> inspections = new ArrayList<>();
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(inspectionRepository.findByReturnIdForUpdate(returnId)).thenAnswer(call -> List.copyOf(inspections));
        when(inspectionRepository.findQcProgressByReturnId(returnId)).thenAnswer(call -> List.copyOf(inspections));
        if (destination != null) {
            when(batchRepository.findFirstByInventoryItemId(item.getId())).thenReturn(Optional.of(batch));
            when(balanceRepository.findForUpdateByBatchId(batch.getId(), destination.getId())).thenReturn(Optional.of(stock));
        }
        for (int count = 1; count <= 3; count++) {
            CustomerReturnInspection inspection = inspection(result, destination, "1.00");
            inspection.getLines().getFirst().setNote(null);
            inspections.add(inspection);
            attachInbox(inspection);
            when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId())).thenReturn(Optional.of(inspection));
            service.postInspection(inspection.getId());
            UUID transactionId = inspection.getLines().getFirst().getTransactionId();
            service.postInspection(inspection.getId());
            service.postInspection(inspection.getId());
            assertThat(inspection.getLines().getFirst().getTransactionId()).isEqualTo(transactionId);
            assertThat(service.getQcSummary(returnId).lines().getFirst().postedQcQuantity()).isEqualByComparingTo(Integer.toString(count));
        }
        assertThat(inspections.stream().map(CustomerReturnInspection::getCommandId).distinct().count()).isEqualTo(3);
        assertThat(inspections.stream().map(row -> row.getLines().getFirst().getTransactionId()).distinct().count()).isEqualTo(3);
        assertThat(service.getQcSummary(returnId).qcStatus()).isEqualTo(CustomerReturnQcStatus.COMPLETED);
        verify(transactionRepository, times(3)).saveAndFlush(any());
        if (destination == null) {
            verifyNoInteractions(balanceRepository);
        } else {
            assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("13");
        }
    }

    private void stubWriteOffPost(CustomerReturnInspection inspection) {
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId())).thenReturn(Optional.of(inspection));
        when(inspectionRepository.findByReturnIdForUpdate(returnId)).thenReturn(List.of(inspection));
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
    }

    private InventoryInbox attachInbox(CustomerReturnInspection inspection) {
        InventoryInbox inbox = new InventoryInbox();
        inspection.setCommandId(UUID.randomUUID());
        when(orderReturnClient.getInventoryDisposition(returnId, inspection.getCommandId())).thenReturn(
                new OrderReturnInventoryCommandResponse(inspection.getCommandId(), returnId, "PENDING", true,
                        inspection.getLines().stream().map(line -> new OrderReturnInventoryCommandResponse.Line(
                                line.getReturnItemId(), line.getOrderItemId(), line.getProductVariantId(),
                                line.getQuantity(), line.getResult().name(),
                                line.getDestinationLocation() == null ? null : line.getDestinationLocation().getId())).toList()));
        inbox.setCommandId(inspection.getCommandId().toString());
        inbox.setStatus(InboxStatus.RECEIVED);
        inbox.setSagaId(UUID.randomUUID().toString());
        inbox.setCorrelationId(UUID.randomUUID().toString());
        when(inboxRepository.findByCommandIdAndConsumerGroup(inbox.getCommandId(), null)).thenReturn(Optional.of(inbox));
        return inbox;
    }

    @Test
    void postInspectionRetriesPostedInspectionWithoutDuplicatingStockMutationAndRepairsAcknowledgement() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        inspection.setStatus(CustomerReturnInspectionStatus.POSTED);
        InventoryInbox inbox = new InventoryInbox();
        inbox.setCommandId(UUID.randomUUID().toString());
        inbox.setSagaId(UUID.randomUUID().toString());
        inbox.setCorrelationId(UUID.randomUUID().toString());
        inbox.setStatus(InboxStatus.FAILED);
        inspection.setCommandId(UUID.fromString(inbox.getCommandId()));
        inbox.setLastError("acknowledgement interrupted");
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId()))
                .thenReturn(Optional.of(inspection));
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(inboxRepository.findByCommandIdAndConsumerGroup(inbox.getCommandId(), null))
                .thenReturn(Optional.of(inbox));

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(inbox.getInspectionId()).isEqualTo(inspection.getId().toString());
        assertThat(inbox.getProcessedAt()).isNotNull();
        assertThat(inbox.getLastError()).isNull();
        verify(inboxRepository).saveAndFlush(inbox);
        verify(inventoryOutboxPublisher).publishReturnInventoryDispositionProcessed(
                inbox.getCommandId(),
                returnId.toString(),
                inspection.getId().toString(),
                inbox.getSagaId(),
                inbox.getCorrelationId());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void postInspectionRejectsCancelledInspection() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        inspection.setStatus(CustomerReturnInspectionStatus.CANCELLED);
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId()))
                .thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.postInspection(inspection.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cancelled");
    }

    @Test
    void postInspectionRejectsWhenCumulativePostedQuantityWouldExceedReturnedQuantity() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "2.00");
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId()))
                .thenReturn(Optional.of(inspection));
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        CustomerReturnInspection posted = postedInspection("2.00");
        when(inspectionRepository.findByReturnIdForUpdate(returnId))
                .thenReturn(List.of(posted, inspection));

        assertThatThrownBy(() -> service.postInspection(inspection.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds returned quantity");
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void qcSummaryIsPendingWhenNoPostedQuantityExists() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));

        CustomerReturnQcSummaryResponse response = service.getQcSummary(returnId);

        assertThat(response.qcStatus()).isEqualTo(CustomerReturnQcStatus.PENDING);
        assertThat(response.lines().getFirst().remainingQcQuantity()).isEqualByComparingTo("3.00");
    }

    @Test
    void qcSummaryIsPartialWhenSomeQuantityIsPosted() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(inspectionRepository.findQcProgressByReturnId(returnId))
                .thenReturn(List.of(postedInspection("1.00")));

        CustomerReturnQcSummaryResponse response = service.getQcSummary(returnId);

        assertThat(response.qcStatus()).isEqualTo(CustomerReturnQcStatus.PARTIAL);
        assertThat(response.lines().getFirst().postedQcQuantity()).isEqualByComparingTo("1.00");
        assertThat(response.lines().getFirst().remainingQcQuantity()).isEqualByComparingTo("2.00");
    }

    @Test
    void qcSummaryIsCompletedWhenAllQuantityIsPosted() {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(inspectionRepository.findQcProgressByReturnId(returnId))
                .thenReturn(List.of(postedInspection("3.00")));

        CustomerReturnQcSummaryResponse response = service.getQcSummary(returnId);

        assertThat(response.qcStatus()).isEqualTo(CustomerReturnQcStatus.COMPLETED);
        assertThat(response.lines().getFirst().status()).isEqualTo(CustomerReturnQcStatus.COMPLETED);
    }

    @ParameterizedTest
    @CsvSource({
            "SEALED,SEALED,RESTOCK", "SEALED,OPENED,WRITE_OFF",
            "SEALED,DAMAGED,DAMAGED", "OPENED,SEALED,RESTOCK",
            "SEALED,SEALED,DAMAGED", "SEALED,SEALED,WRITE_OFF"
    })
    void inspectionUsesActualConditionAndPreservesOriginalDeclaration(
            OrderReturnCondition declared, CustomerReturnActualCondition actual, CustomerReturnQcResult result) {
        OrderReturnResponse original = orderReturn(OrderReturnStatus.RECEIVED, declared, "3.00");
        OrderReturnItemResponse declaredLine = original.items().getFirst();
        String reason = declared == OrderReturnCondition.SEALED ? "CHANGE_OF_MIND" : "DEFECTIVE";
        when(orderReturnClient.getReturnById(returnId)).thenReturn(new OrderReturnResponse(
                original.id(), original.returnCode(), original.orderId(), original.orderCode(), original.branchId(),
                original.status(), original.note(), original.createdAt(), original.updatedAt(),
                List.of(new OrderReturnItemResponse(declaredLine.id(), declaredLine.orderItemId(), declaredLine.productVariantId(),
                        declaredLine.quantity(), reason, declared, null, declaredLine.skuSnapshot(), declaredLine.productNameSnapshot(), declaredLine.variantNameSnapshot()))));
        stubActiveItem();
        InventoryLocation destination = result == CustomerReturnQcResult.RESTOCK ? shelf
                : result == CustomerReturnQcResult.DAMAGED ? damaged : null;
        if (destination != null) when(locationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        CustomerReturnInspectionResponse response = service.createInspection(returnId,
                actualRequest(actual, result, destination == null ? null : destination.getId()));
        assertThat(response.lines().getFirst().declaredCondition()).isEqualTo(declared.name());
        assertThat(response.lines().getFirst().condition()).isEqualTo(declared.name());
        assertThat(response.lines().getFirst().actualCondition()).isEqualTo(actual);
        assertThat(response.lines().getFirst().result()).isEqualTo(result);
        assertThat(original.items().getFirst().condition()).isEqualTo(declared);
    }

    @ParameterizedTest
    @EnumSource(value = CustomerReturnActualCondition.class, names = {"OPENED", "USED", "DAMAGED", "SPOILED"})
    void everyNonSealedActualConditionRejectsRestock(CustomerReturnActualCondition actual) {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        assertThatThrownBy(() -> service.createInspection(returnId, actualRequest(actual, CustomerReturnQcResult.RESTOCK, shelf.getId())))
                .hasMessageContaining("RESTOCK is not allowed for QC actual condition " + actual);
        verifyNoInteractions(balanceRepository, transactionRepository);
    }

    @Test
    void directQcCreationRequiresActualCondition() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        assertThatThrownBy(() -> service.createInspection(returnId, actualRequest(null, CustomerReturnQcResult.WRITE_OFF, null)))
                .hasMessageContaining("QC actual condition is required");
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void orderCommandCanOpenDraftWithoutActualConditionButCannotPostIt() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        stubActiveItem();
        UUID commandId = UUID.randomUUID();
        authorize(commandId, CustomerReturnQcResult.WRITE_OFF, BigDecimal.ONE, null);
        CustomerReturnInspectionResponse response = service.createInspection(returnId, commandId,
                actualRequest(null, CustomerReturnQcResult.WRITE_OFF, null));
        ArgumentCaptor<CustomerReturnInspection> saved = ArgumentCaptor.forClass(CustomerReturnInspection.class);
        verify(inspectionRepository).saveAndFlush(saved.capture());
        CustomerReturnInspection draft = saved.getValue();
        assertThat(response.lines().getFirst().actualCondition()).isNull();
        stubWriteOffPost(draft);
        assertThatThrownBy(() -> service.postInspection(draft.getId())).hasMessageContaining("QC actual condition is required");
        verifyNoInteractions(balanceRepository, transactionRepository, inventoryOutboxPublisher);
    }

    @Test
    void postRevalidatesActualConditionRatherThanTrustingSavedRestockResult() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        inspection.getLines().getFirst().setActualCondition(CustomerReturnActualCondition.OPENED);
        stubWriteOffPost(inspection);
        assertThatThrownBy(() -> service.postInspection(inspection.getId()))
                .hasMessageContaining("RESTOCK is not allowed for QC actual condition OPENED");
        verifyNoInteractions(balanceRepository, transactionRepository, inventoryOutboxPublisher);
    }

    @Test
    void qcCanCorrectDraftConditionResultDestinationAndNoteBeforePostingAndAck() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        line.setActualCondition(null);
        stubWriteOffPost(inspection);
        InventoryInbox inbox = attachInbox(inspection);
        PostCustomerReturnInspectionRequest correction = new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, null,
                        CustomerReturnActualCondition.OPENED, CustomerReturnQcResult.WRITE_OFF, "Seal opened during inspection")));
        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId(), correction);
        assertThat(response.lines().getFirst().declaredCondition()).isEqualTo("SEALED");
        assertThat(response.lines().getFirst().actualCondition()).isEqualTo(CustomerReturnActualCondition.OPENED);
        assertThat(line.getResult()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
        assertThat(line.getDestinationLocation()).isNull();
        assertThat(line.getNote()).isEqualTo("Seal opened during inspection");
        assertThat(line.getPosted()).isTrue();
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        verify(inventoryOutboxPublisher).publishReturnInventoryDispositionProcessed(
                inbox.getCommandId(), returnId.toString(), inspection.getId().toString(), inbox.getSagaId(), inbox.getCorrelationId());
        verifyNoInteractions(balanceRepository);
    }

    @Test
    void qcCanRestockActuallySealedGoodsEvenWhenDeclaredOpened() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.DAMAGED, damaged, "1.00");
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        stubWriteOffPost(inspection);
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED, OrderReturnCondition.OPENED, "3.00"));
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));
        when(batchRepository.findFirstByInventoryItemId(item.getId())).thenReturn(Optional.of(batch));
        InventoryBalance balance = balance("5", "0", "0", shelf);
        when(balanceRepository.findForUpdateByBatchId(batch.getId(), shelf.getId())).thenReturn(Optional.of(balance));
        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId(), new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, shelf.getId(),
                        CustomerReturnActualCondition.SEALED, CustomerReturnQcResult.RESTOCK, "Actually sealed"))));
        assertThat(response.lines().getFirst().declaredCondition()).isEqualTo("OPENED");
        assertThat(response.lines().getFirst().actualCondition()).isEqualTo(CustomerReturnActualCondition.SEALED);
        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("6");
        assertThat(line.getResult()).isEqualTo(CustomerReturnQcResult.RESTOCK);
    }

    @Test
    void postRejectsInvalidCorrectionBeforeAnyMovement() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.RESTOCK, shelf, "1.00");
        stubWriteOffPost(inspection);
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        assertThatThrownBy(() -> service.postInspection(inspection.getId(), new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, shelf.getId(),
                        CustomerReturnActualCondition.USED, CustomerReturnQcResult.RESTOCK, null)))))
                .hasMessageContaining("RESTOCK is not allowed for QC actual condition USED");
        verifyNoInteractions(balanceRepository, transactionRepository, inventoryOutboxPublisher);
    }

    @Test
    void postedHistoricalInspectionRemainsImmutableWithoutBackfilledActualCondition() {
        CustomerReturnInspection inspection = postedInspection("1.00");
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        line.setActualCondition(null);
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId())).thenReturn(Optional.of(inspection));
        service.postInspection(inspection.getId(), new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, null,
                        CustomerReturnActualCondition.SEALED, CustomerReturnQcResult.RESTOCK, "Changed"))));
        assertThat(line.getActualCondition()).isNull();
        assertThat(line.getResult()).isEqualTo(CustomerReturnQcResult.WRITE_OFF);
        assertThat(line.getNote()).isNull();
        verifyNoInteractions(balanceRepository, transactionRepository);
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void postedLineInsideProcessingInspectionCannotBeEdited() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        inspection.setStatus(CustomerReturnInspectionStatus.PROCESSING);
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        line.setPosted(true);
        stubWriteOffPost(inspection);
        assertThatThrownBy(() -> service.postInspection(inspection.getId(), new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, null,
                        CustomerReturnActualCondition.OPENED, CustomerReturnQcResult.WRITE_OFF, "Changed")))))
                .hasMessageContaining("Posted inspection lines cannot be changed");
        verifyNoInteractions(balanceRepository, transactionRepository);
    }

    @Test
    void correctionsCannotIntroduceNewQuarantine() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.DAMAGED, damaged, "1.00");
        stubWriteOffPost(inspection);
        CustomerReturnInspectionLine line = inspection.getLines().getFirst();
        assertThatThrownBy(() -> service.postInspection(inspection.getId(), new PostCustomerReturnInspectionRequest(List.of(
                new PostCustomerReturnInspectionRequest.LineOverride(line.getId(), returnItemId, quarantine.getId(),
                        CustomerReturnActualCondition.SEALED, CustomerReturnQcResult.QUARANTINE, null)))))
                .hasMessageContaining("QUARANTINE is no longer supported");
        verifyNoInteractions(balanceRepository, transactionRepository);
    }

    private CreateCustomerReturnInspectionRequest actualRequest(
            CustomerReturnActualCondition actual, CustomerReturnQcResult result, UUID destination) {
        return new CreateCustomerReturnInspectionRequest(null, List.of(new CreateCustomerReturnInspectionLineRequest(
                returnItemId, BigDecimal.ONE, result, destination, "QC observation", actual)));
    }

    private void stubReceivedReturn(OrderReturnCondition condition, String quantity) {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(
                OrderReturnStatus.RECEIVED,
                condition,
                quantity));
    }

    private void authorize(UUID commandId, CustomerReturnQcResult result, BigDecimal quantity, InventoryLocation location) {
        when(orderReturnClient.getInventoryDisposition(returnId, commandId)).thenReturn(
                new OrderReturnInventoryCommandResponse(commandId, returnId, "PENDING", true, List.of(
                        new OrderReturnInventoryCommandResponse.Line(returnItemId, orderItemId, productVariantId,
                                quantity, result.name(), location == null ? null : location.getId()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "PROCESSED", "PENDING"})
    void delayedCommandWithoutExecutionAuthorizationCannotCreateInspection(String status) {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        UUID commandId = UUID.randomUUID();
        when(orderReturnClient.getInventoryDisposition(returnId, commandId)).thenReturn(
                new OrderReturnInventoryCommandResponse(commandId, returnId, status, false, List.of()));
        assertThatThrownBy(() -> service.createInspection(returnId, commandId,
                actualRequest(null, CustomerReturnQcResult.WRITE_OFF, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not executable");
        verifyNoInteractions(transactionRepository, balanceRepository, inventoryOutboxPublisher);
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void unverifiedOrderContractCannotCreateInspectionOrFailureAcknowledgement() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        assertThatThrownBy(() -> service.createInspection(returnId, UUID.randomUUID(),
                actualRequest(null, CustomerReturnQcResult.WRITE_OFF, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("authorization is unavailable");
        verifyNoInteractions(transactionRepository, balanceRepository, inventoryOutboxPublisher);
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void authorizedCommandCannotChangeItsReservedQuantity() {
        stubReceivedReturn(OrderReturnCondition.SEALED, "3.00");
        UUID commandId = UUID.randomUUID();
        authorize(commandId, CustomerReturnQcResult.WRITE_OFF, new BigDecimal("2"), null);
        assertThatThrownBy(() -> service.createInspection(returnId, commandId,
                actualRequest(null, CustomerReturnQcResult.WRITE_OFF, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("does not match");
        verify(inspectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void commandThatFailedAfterInspectionCreationCannotMoveStock() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        UUID commandId = UUID.randomUUID();
        inspection.setCommandId(commandId);
        stubWriteOffPost(inspection);
        when(orderReturnClient.getInventoryDisposition(returnId, commandId)).thenReturn(
                new OrderReturnInventoryCommandResponse(commandId, returnId, "FAILED", false, List.of()));
        assertThatThrownBy(() -> service.postInspection(inspection.getId()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not executable");
        verifyNoInteractions(transactionRepository, balanceRepository, inventoryOutboxPublisher);
    }

    @Test
    void refreshedPostedInspectionCannotBePostedTwiceAfterWaitingForLock() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        stubWriteOffPost(inspection);
        doAnswer(call -> {
            inspection.setStatus(CustomerReturnInspectionStatus.POSTED);
            inspection.getLines().getFirst().setPosted(true);
            return null;
        }).when(entityManager).refresh(inspection);
        assertThat(service.postInspection(inspection.getId()).status()).isEqualTo(CustomerReturnInspectionStatus.POSTED);
        verifyNoInteractions(transactionRepository, balanceRepository);
    }

    private void stubActiveItem() {
        when(itemRepository.findByReferenceIdAndItemType(productVariantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
    }

    private void stubPost(CustomerReturnInspection inspection, InventoryBalance balance) {
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId()))
                .thenReturn(Optional.of(inspection));
        when(inspectionRepository.findByReturnIdForUpdate(returnId)).thenReturn(List.of(inspection));
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(batchRepository.findFirstByInventoryItemId(item.getId())).thenReturn(Optional.of(batch));
        when(balanceRepository.findForUpdateByBatchId(batch.getId(), inspection.getLines().getFirst()
                .getDestinationLocation().getId()))
                .thenReturn(Optional.of(balance));
    }

    private CreateCustomerReturnInspectionRequest request(
            UUID lineReturnItemId,
            String quantity,
            CustomerReturnQcResult result,
            UUID destinationLocationId,
            String note) {
        return new CreateCustomerReturnInspectionRequest(
                null,
                List.of(requestLine(lineReturnItemId, quantity, result, destinationLocationId, note)));
    }

    private CreateCustomerReturnInspectionLineRequest requestLine(
            UUID lineReturnItemId,
            String quantity,
            CustomerReturnQcResult result,
            UUID destinationLocationId,
            String note) {
        return new CreateCustomerReturnInspectionLineRequest(
                lineReturnItemId,
                new BigDecimal(quantity),
                result,
                destinationLocationId,
                note,
                CustomerReturnActualCondition.SEALED);
    }

    private CustomerReturnInspection inspection(
            CustomerReturnQcResult result,
            InventoryLocation destination,
            String quantity) {
        CustomerReturnInspection inspection = new CustomerReturnInspection();
        inspection.setId(UUID.randomUUID());
        inspection.setInspectionCode("CR-QC-TEST");
        inspection.setReturnId(returnId);
        inspection.setReturnCode("RET-001");
        inspection.setBranchId(branchId);
        inspection.setOrderId(UUID.randomUUID());
        inspection.setOrderCode("ORD-001");
        inspection.setStatus(CustomerReturnInspectionStatus.CREATED);

        CustomerReturnInspectionLine line = new CustomerReturnInspectionLine();
        line.setId(UUID.randomUUID());
        line.setReturnItemId(returnItemId);
        line.setOrderItemId(orderItemId);
        line.setProductVariantId(productVariantId);
        line.setInventoryItem(item);
        line.setQuantity(new BigDecimal(quantity));
        line.setResult(result);
        line.setActualCondition(CustomerReturnActualCondition.SEALED);
        line.setDestinationLocation(destination);
        line.setPosted(false);
        inspection.addLine(line);
        return inspection;
    }

    private CustomerReturnInspection postedInspection(String quantity) {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, quantity);
        inspection.setStatus(CustomerReturnInspectionStatus.POSTED);
        inspection.getLines().getFirst().setPosted(true);
        return inspection;
    }

    private OrderReturnResponse orderReturn(OrderReturnStatus status) {
        return orderReturn(status, OrderReturnCondition.SEALED, "3.00");
    }

    private OrderReturnResponse orderReturn(
            OrderReturnStatus status,
            OrderReturnCondition condition,
            String quantity) {
        return new OrderReturnResponse(
                returnId,
                "RET-001",
                UUID.randomUUID(),
                "ORD-001",
                branchId,
                status,
                null,
                Instant.now(),
                Instant.now(),
                List.of(new OrderReturnItemResponse(
                        returnItemId,
                        orderItemId,
                        productVariantId,
                        new BigDecimal(quantity),
                        "DEFECTIVE",
                        condition,
                        null,
                        "SKU-001",
                        "Tra sen",
                        "100g")));
    }

    private CustomerReturnInspection assignInspectionIds(CustomerReturnInspection inspection) {
        if (inspection.getId() == null) {
            inspection.setId(UUID.randomUUID());
        }
        for (CustomerReturnInspectionLine line : inspection.getLines()) {
            if (line.getId() == null) {
                line.setId(UUID.randomUUID());
            }
        }
        return inspection;
    }

    private InventoryTransaction assignTransactionId(InventoryTransaction transaction) {
        if (transaction.getId() == null) {
            transaction.setId(UUID.randomUUID());
        }
        return transaction;
    }

    private InventoryItem inventoryItem() {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setId(UUID.randomUUID());
        inventoryItem.setReferenceId(productVariantId);
        inventoryItem.setReferenceCode("SKU-001");
        inventoryItem.setName("Tra sen");
        inventoryItem.setItemType(InventoryItemType.PRODUCT_VARIANT);
        inventoryItem.setStatus(InventoryStatus.ACTIVE);
        inventoryItem.setReorderPoint(5);
        return inventoryItem;
    }

    private InventoryBatch batch(InventoryItem inventoryItem) {
        InventoryBatch inventoryBatch = new InventoryBatch();
        inventoryBatch.setId(UUID.randomUUID());
        inventoryBatch.setInventoryItem(inventoryItem);
        inventoryBatch.setBatchCode("BATCH-001");
        inventoryBatch.setStatus(InventoryBatchStatus.AVAILABLE);
        return inventoryBatch;
    }

    private InventoryLocation location(InventoryLocationType type, UUID locationBranchId) {
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setBranchId(locationBranchId);
        location.setType(type);
        location.setStatus(InventoryStatus.ACTIVE);
        location.setCode(type.name());
        location.setName(type.name());
        return location;
    }

    private InventoryBalance balance(String onHand, String reserved, String blocked, InventoryLocation location) {
        InventoryBalance balance = new InventoryBalance();
        balance.setId(UUID.randomUUID());
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(onHand));
        balance.setQuantityReserved(new BigDecimal(reserved));
        balance.setQuantityBlocked(new BigDecimal(blocked));
        return balance;
    }
}
