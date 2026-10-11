package org.hvtra.iventoryservice.replenishment;

import org.hvtra.iventoryservice.document.repository.InventoryDocumentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.replenishment.dto.CreateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentLineRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.service.ShelfReplenishmentServiceImpl;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShelfReplenishmentServiceImplTest {
    @Mock
    private InventoryDocumentRepository documentRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private BusinessSequenceService sequenceService;

    @InjectMocks
    private ShelfReplenishmentServiceImpl service;

    private UUID variantId;
    private InventoryItem item;
    private InventoryLocation warehouse;
    private InventoryLocation shelf;

    @BeforeEach
    void setUp() {
        variantId = UUID.randomUUID();
        item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setReferenceId(variantId);
        item.setReferenceCode("SKU-TEA");
        item.setName("Trà sen 100g");
        item.setItemType(InventoryItemType.PRODUCT_VARIANT);
        item.setStatus(InventoryStatus.ACTIVE);

        warehouse = location(InventoryLocationType.WAREHOUSE, null);
        shelf = location(InventoryLocationType.SHELF, warehouse);

        when(itemRepository.findByReferenceIdInAndItemType(any(), eq(InventoryItemType.PRODUCT_VARIANT)))
                .thenReturn(List.of(item));
        when(locationRepository.findById(shelf.getId())).thenReturn(Optional.of(shelf));
        when(locationRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(sequenceService.nextCode(any(), anyInt())).thenReturn("NKE-00001");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.sumAvailableByItemsAndLocations(any(), any())).thenReturn(List.of());
        authenticateWith("ROLE_INVENTORY_STAFF");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_withSubmitFalse_savesDraftWithWarehouseAsSourceAndShelfAsDestination() {
        ShelfReplenishmentResponse response = service.create(request(false, null, "1"));

        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.DRAFT);
        assertThat(response.documentCode()).isEqualTo("NKE-00001");
        assertThat(response.warehouseId()).isEqualTo(warehouse.getId());
        assertThat(response.shelfId()).isEqualTo(shelf.getId());
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().quantity()).isEqualByComparingTo("1");
        assertThat(response.lines().getFirst().productVariantId()).isEqualTo(variantId);
    }

    @Test
    void create_withSubmitTrue_sendsSlipToWarehouse() {
        ShelfReplenishmentResponse response = service.create(request(true, null, "20"));

        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.PENDING_APPROVAL);
    }

    @Test
    void create_allowsPosStaffButRejectsUnrelatedRole() {
        authenticateWith("ROLE_pos_staff");
        assertThat(service.create(request(true, null, "1")).status())
                .isEqualTo(InventoryDocumentStatus.PENDING_APPROVAL);

        authenticateWith("ROLE_ACCOUNTANT");
        assertThatThrownBy(() -> service.create(request(true, null, "1")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_withSameReference_updatesTheOpenSlipInsteadOfCreatingAnother() {
        InventoryDocument open = document(InventoryDocumentStatus.DRAFT, "1");
        when(documentRepository.findFirstByDocumentTypeAndReferenceIdAndStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                eq(InventoryDocumentType.TRANSFER_RECEIPT), eq("order-1"), any()))
                .thenReturn(Optional.of(open));

        ShelfReplenishmentResponse response = service.create(request(true, "order-1", "20"));

        assertThat(response.id()).isEqualTo(open.getId());
        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.PENDING_APPROVAL);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().quantity()).isEqualByComparingTo("20");
        verify(sequenceService, never()).nextCode(any(), anyInt());
    }

    @Test
    void post_movesStockFefoFromWarehouseToShelfKeepingBatches() {
        InventoryDocument document = document(InventoryDocumentStatus.PENDING_APPROVAL, "8");
        when(documentRepository.findByIdAndIsDeletedFalse(document.getId())).thenReturn(Optional.of(document));
        InventoryBalance firstBatch = balance(warehouse, "5");
        InventoryBalance secondBatch = balance(warehouse, "10");
        InventoryBalance shelfBalance = balance(shelf, "2");
        shelfBalance.setInventoryBatch(firstBatch.getInventoryBatch());
        when(balanceRepository.findFefoAvailableForUpdate(eq(item.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(firstBatch, secondBatch));
        when(balanceRepository.findForUpdateByBatchId(firstBatch.getInventoryBatch().getId(), shelf.getId()))
                .thenReturn(Optional.of(shelfBalance));
        when(balanceRepository.findForUpdateByBatchId(secondBatch.getInventoryBatch().getId(), shelf.getId()))
                .thenReturn(Optional.empty());

        ShelfReplenishmentResponse response = service.post(document.getId());

        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.POSTED);
        assertThat(firstBatch.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(secondBatch.getQuantityOnHand()).isEqualByComparingTo("7");
        assertThat(shelfBalance.getQuantityOnHand()).isEqualByComparingTo("7");

        ArgumentCaptor<InventoryBalance> saved = ArgumentCaptor.forClass(InventoryBalance.class);
        verify(balanceRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        InventoryBalance newShelfBalance = saved.getAllValues().stream()
                .filter(balance -> balance.getLocation() == shelf && balance != shelfBalance)
                .findFirst()
                .orElseThrow();
        assertThat(newShelfBalance.getQuantityOnHand()).isEqualByComparingTo("3");
        assertThat(newShelfBalance.getInventoryItem()).isEqualTo(item);
        assertThat(newShelfBalance.getInventoryBatch()).isEqualTo(secondBatch.getInventoryBatch());

        ArgumentCaptor<InventoryTransaction> transaction = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getType()).isEqualTo(InventoryTransactionType.TRANSFER);
        assertThat(transaction.getValue().getLines()).hasSize(4);
        assertThat(transaction.getValue().getLines().stream()
                .map(line -> line.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
    }

    @Test
    void post_rejectsWhenWarehouseIsShortAndMovesNothing() {
        InventoryDocument document = document(InventoryDocumentStatus.PENDING_APPROVAL, "8");
        when(documentRepository.findByIdAndIsDeletedFalse(document.getId())).thenReturn(Optional.of(document));
        InventoryBalance onlyBatch = balance(warehouse, "5");
        when(balanceRepository.findFefoAvailableForUpdate(eq(item.getId()), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(onlyBatch));

        assertThatThrownBy(() -> service.post(document.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đủ hàng");

        assertThat(onlyBatch.getQuantityOnHand()).isEqualByComparingTo("5");
        verify(transactionRepository, never()).save(any());
        assertThat(document.getStatus()).isEqualTo(InventoryDocumentStatus.PENDING_APPROVAL);
    }

    @Test
    void post_isIdempotentAndRestrictedToWarehouseRoles() {
        InventoryDocument posted = document(InventoryDocumentStatus.POSTED, "8");
        when(documentRepository.findByIdAndIsDeletedFalse(posted.getId())).thenReturn(Optional.of(posted));

        assertThat(service.post(posted.getId()).status()).isEqualTo(InventoryDocumentStatus.POSTED);
        verify(transactionRepository, never()).save(any());

        authenticateWith("ROLE_POS_STAFF");
        assertThatThrownBy(() -> service.post(posted.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_rejectsPostedSlip() {
        InventoryDocument posted = document(InventoryDocumentStatus.POSTED, "8");
        when(documentRepository.findByIdAndIsDeletedFalse(posted.getId())).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.cancel(posted.getId(), "nhầm"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void post_byWarehouseStaffCountsAsReviewed() {
        InventoryDocument document = document(InventoryDocumentStatus.PENDING_APPROVAL, "5");
        when(documentRepository.findByIdAndIsDeletedFalse(document.getId())).thenReturn(Optional.of(document));
        when(balanceRepository.findFefoAvailableForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(balance(warehouse, "10")));
        when(balanceRepository.findForUpdateByBatchId(any(), any())).thenReturn(Optional.empty());

        ShelfReplenishmentResponse response = service.post(document.getId());

        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.POSTED);
        assertThat(response.pendingReview()).isFalse();
        assertThat(response.autoIssued()).isFalse();
    }

    @Test
    void autoIssueForOrder_movesStockImmediatelyAndLeavesSlipPendingReview() {
        UUID orderId = UUID.randomUUID();
        when(documentRepository.findFirstByDocumentTypeAndReferenceIdAndStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                any(), eq(orderId.toString()), any())).thenReturn(Optional.empty());
        when(locationRepository.findFirstByTypeAndStatusOrderByCreatedAtAsc(
                InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)).thenReturn(Optional.of(warehouse));
        when(locationRepository.findByParentLocation_IdAndTypeAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                warehouse.getId(), InventoryLocationType.SHELF, InventoryStatus.ACTIVE)).thenReturn(List.of(shelf));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            InventoryDocument saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        InventoryBalance source = balance(warehouse, "60");
        when(balanceRepository.findFefoAvailableForUpdate(any(), eq(warehouse.getId()), any(), any()))
                .thenReturn(List.of(source));
        when(balanceRepository.findForUpdateByBatchId(any(), eq(shelf.getId()))).thenReturn(Optional.empty());
        // Không cần role kho: đây là luồng nội bộ khi POS bán hàng.
        authenticateWith("ROLE_POS_STAFF");

        ShelfReplenishmentResponse response = service.autoIssueForOrder(
                orderId, null, java.util.Map.of(variantId, new BigDecimal("20")));

        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.POSTED);
        assertThat(response.autoIssued()).isTrue();
        assertThat(response.pendingReview()).isTrue();
        assertThat(response.referenceId()).isEqualTo(orderId.toString());
        assertThat(response.lines().getFirst().quantity()).isEqualByComparingTo("20");
        assertThat(source.getQuantityOnHand()).isEqualByComparingTo("40");
    }

    @Test
    void autoIssueForOrder_withNoShortageCancelsTheOpenSlipOfTheOrder() {
        UUID orderId = UUID.randomUUID();
        InventoryDocument open = document(InventoryDocumentStatus.PENDING_APPROVAL, "3");
        when(documentRepository.findFirstByDocumentTypeAndReferenceIdAndStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                any(), eq(orderId.toString()), any())).thenReturn(Optional.of(open));

        ShelfReplenishmentResponse response = service.autoIssueForOrder(orderId, null, java.util.Map.of());

        assertThat(response).isNull();
        assertThat(open.getStatus()).isEqualTo(InventoryDocumentStatus.CANCELLED);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void review_marksAutoIssuedSlipAsReviewedAndIsRestrictedToManagers() {
        InventoryDocument document = document(InventoryDocumentStatus.POSTED, "5");
        document.setReasonCode("POS_AUTO_ISSUE");
        when(documentRepository.findByIdAndIsDeletedFalse(document.getId())).thenReturn(Optional.of(document));

        authenticateWith("ROLE_INVENTORY_STAFF");
        assertThatThrownBy(() -> service.review(document.getId())).isInstanceOf(AccessDeniedException.class);

        authenticateWith("ROLE_INVENTORY_MANAGER");
        ShelfReplenishmentResponse response = service.review(document.getId());

        assertThat(response.pendingReview()).isFalse();
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.autoIssued()).isTrue();
    }

    private CreateShelfReplenishmentRequest request(boolean submit, String referenceId, String quantity) {
        return new CreateShelfReplenishmentRequest(
                shelf.getId(), null, null, referenceId, null, submit,
                List.of(new ShelfReplenishmentLineRequest(variantId, new BigDecimal(quantity), null)));
    }

    private InventoryDocument document(InventoryDocumentStatus status, String quantity) {
        InventoryDocument document = new InventoryDocument();
        document.setId(UUID.randomUUID());
        document.setDocumentCode("NKE-00009");
        document.setDocumentType(InventoryDocumentType.TRANSFER_RECEIPT);
        document.setStatus(status);
        document.setLocationId(shelf.getId());
        document.setSourceLocation(warehouse);
        document.setDestinationLocation(shelf);
        InventoryDocumentLine line = new InventoryDocumentLine();
        line.setId(UUID.randomUUID());
        line.setLineNo(1);
        line.setInventoryItem(item);
        line.setOrderedQuantity(new BigDecimal(quantity));
        document.addLine(line);
        return document;
    }

    private InventoryBalance balance(InventoryLocation location, String onHand) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(UUID.randomUUID());
        batch.setInventoryItem(item);
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

    private InventoryLocation location(InventoryLocationType type, InventoryLocation parent) {
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setBranchId(UUID.randomUUID());
        location.setType(type);
        location.setStatus(InventoryStatus.ACTIVE);
        location.setParentLocation(parent);
        location.setName(type.name());
        location.setCode(type.name());
        return location;
    }

    private void authenticateWith(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
