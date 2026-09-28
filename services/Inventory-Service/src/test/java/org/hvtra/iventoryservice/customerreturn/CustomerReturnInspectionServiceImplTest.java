package org.hvtra.iventoryservice.customerreturn;

import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.impl.CustomerReturnInspectionServiceImpl;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
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
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
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
                inventoryOutboxPublisher);
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

        lenient().when(inspectionRepository.existsByInspectionCode(anyString())).thenReturn(false);
        lenient().when(inspectionRepository.saveAndFlush(any(CustomerReturnInspection.class)))
                .thenAnswer(invocation -> assignInspectionIds(invocation.getArgument(0)));
        lenient().when(transactionRepository.saveAndFlush(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> assignTransactionId(invocation.getArgument(0)));
        lenient().when(inspectionRepository.findByReturnIdAndStatus(
                any(),
                eq(CustomerReturnInspectionStatus.POSTED)))
                .thenReturn(List.of());
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
    void createInspectionRejectsOpenedRestock() {
        stubReceivedReturn(OrderReturnCondition.OPENED, "3.00");

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.RESTOCK,
                shelf.getId(),
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESTOCK is not allowed");
    }

    @Test
    void createInspectionAllowsOpenedQuarantine() {
        stubReceivedReturn(OrderReturnCondition.OPENED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(quarantine.getId())).thenReturn(Optional.of(quarantine));

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                quarantine.getId(),
                null));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.QUARANTINE);
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
    void createInspectionAllowsDamagedQuarantine() {
        stubReceivedReturn(OrderReturnCondition.DAMAGED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(quarantine.getId())).thenReturn(Optional.of(quarantine));

        CustomerReturnInspectionResponse response = service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                quarantine.getId(),
                null));

        assertThat(response.lines().getFirst().result()).isEqualTo(CustomerReturnQcResult.QUARANTINE);
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
        when(inspectionRepository.findQcProgressByReturnId(returnId))
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
        stubActiveItem();

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destinationLocationId is required");
    }

    @Test
    void createInspectionRejectsQuarantineToNonQuarantineLocation() {
        stubReceivedReturn(OrderReturnCondition.DAMAGED, "3.00");
        stubActiveItem();
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() -> service.createInspection(returnId, request(
                returnItemId,
                "1.00",
                CustomerReturnQcResult.QUARANTINE,
                shelf.getId(),
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("QUARANTINE location");
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

    @Test
    void postQuarantineIncreasesOnHandAndBlocked() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.QUARANTINE, quarantine, "1.00");
        InventoryBalance balance = balance("5.00", "0.00", "2.00", quarantine);
        stubPost(inspection, balance);

        service.postInspection(inspection.getId());

        assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("6.00");
        assertThat(balance.getQuantityBlocked()).isEqualByComparingTo("3.00");
    }

    @Test
    void postWriteOffCreatesAuditTransactionWithoutBalanceChange() {
        CustomerReturnInspection inspection = inspection(CustomerReturnQcResult.WRITE_OFF, null, "1.00");
        when(inspectionRepository.findByIdAndIsDeletedFalse(inspection.getId()))
                .thenReturn(Optional.of(inspection));
        when(inspectionRepository.findByReturnIdForUpdate(returnId)).thenReturn(List.of(inspection));
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(OrderReturnStatus.RECEIVED));
        when(disposalRequestRepository.findBySourceInspectionId(inspection.getId()))
                .thenReturn(Optional.empty());

        CustomerReturnInspectionResponse response = service.postInspection(inspection.getId());

        ArgumentCaptor<DisposalRequest> captor = ArgumentCaptor.forClass(DisposalRequest.class);
        verify(disposalRequestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLines()).containsExactly(inspection.getLines().getFirst());
        assertThat(response.status()).isEqualTo(CustomerReturnInspectionStatus.PROCESSING);
        assertThat(inspection.getLines().getFirst().getPosted()).isFalse();
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(balanceRepository, never()).save(any());
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

    private void stubReceivedReturn(OrderReturnCondition condition, String quantity) {
        when(orderReturnClient.getReturnById(returnId)).thenReturn(orderReturn(
                OrderReturnStatus.RECEIVED,
                condition,
                quantity));
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
                note);
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
