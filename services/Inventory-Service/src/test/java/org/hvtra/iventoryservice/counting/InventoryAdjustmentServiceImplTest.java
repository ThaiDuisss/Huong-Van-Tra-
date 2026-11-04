package org.hvtra.iventoryservice.counting;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest.AdjustmentLineRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentLineResponse;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.counting.mapper.AdjustmentMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryAdjustmentRepository;
import org.hvtra.iventoryservice.counting.repository.InventoryCountRepository;
import org.hvtra.iventoryservice.counting.service.InventoryAdjustmentServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
@DisplayName("InventoryAdjustmentServiceImpl Unit Tests")
class InventoryAdjustmentServiceImplTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440500");
    private static final UUID COUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID BRANCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    private static final UUID MANAGER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");
    private static final UUID STAFF_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    private static final UUID ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
    private static final UUID UNIT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    private static final UUID LOCATION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440200");
    private static final UUID BATCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Mock
    private InventoryAdjustmentRepository adjustmentRepository;
    @Mock
    private InventoryCountRepository countRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;

    private InventoryAdjustmentServiceImpl service;
    private InventoryItem item;
    private InventoryLocation location;
    private InventoryBatch batch;

    @BeforeEach
    void setUp() {
        service = new InventoryAdjustmentServiceImpl(adjustmentRepository, countRepository, balanceRepository,
                itemRepository, locationRepository, transactionRepository, new AdjustmentMapper());

        item = new InventoryItem();
        item.setId(ITEM_ID);
        item.setUnitId(UNIT_ID);
        item.setName("Trà Shan Tuyết hộp 100g");
        item.setStatus(InventoryStatus.ACTIVE);
        location = new InventoryLocation();
        location.setId(LOCATION_ID);
        location.setBranchId(BRANCH_ID);
        location.setStatus(InventoryStatus.ACTIVE);
        batch = new InventoryBatch();
        batch.setId(BATCH_ID);
        batch.setBatchCode("LOT-20261103-001");
        batch.setInventoryItem(item);

        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
        when(adjustmentRepository.save(any(InventoryAdjustment.class))).thenAnswer(invocation -> {
            InventoryAdjustment document = invocation.getArgument(0);
            if (document.getId() == null) {
                document.setId(DOCUMENT_ID);
            }
            return document;
        });
        when(countRepository.save(any(InventoryCount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticate(MANAGER_ID.toString(), "MANAGER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("findAll / findById")
    class QueryTests {

        @Test
        @DisplayName("findAll maps matching adjustments to a page")
        void findAll_WhenDocumentsMatch_ShouldReturnPage() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(adjustmentRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(manualDocument("5.00", InventoryDocumentStatus.DRAFT)), pageable, 1));

            PageResponse<AdjustmentResponse> page = service.findAll(BRANCH_ID, InventoryDocumentStatus.DRAFT, "ADJ-2026", pageable);

            assertThat(page.content()).singleElement()
                    .extracting(AdjustmentResponse::documentCode)
                    .isEqualTo("ADJ-20261103-9F8E7D6C");
        }

        @Test
        @DisplayName("findAll returns an empty page when nothing matches")
        void findAll_WhenNothingMatches_ShouldReturnEmptyPage() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(adjustmentRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            assertThat(service.findAll(null, null, null, pageable).content()).isEmpty();
        }

        @Test
        @DisplayName("findById returns the adjustment with its lines")
        void findById_WhenDocumentExists_ShouldReturnResponse() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.DRAFT));

            AdjustmentResponse response = service.findById(DOCUMENT_ID);

            assertThat(response.id()).isEqualTo(DOCUMENT_ID);
            assertThat(response.lines()).singleElement()
                    .extracting(AdjustmentLineResponse::adjustmentQuantity)
                    .isEqualTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("findById throws ResourceNotFoundException for unknown id")
        void findById_WhenMissing_ShouldThrowNotFound() {
            when(adjustmentRepository.findByIdAndIsDeletedFalse(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(UNKNOWN_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Inventory adjustment not found: " + UNKNOWN_ID);
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("create from an approved count copies variance lines and links the count")
        void create_WhenFromApprovedCount_ShouldBuildStockCountAdjustment() {
            InventoryCount count = approvedCount();
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            AdjustmentResponse response = service.create(new CreateAdjustmentRequest(
                    COUNT_ID, BRANCH_ID, "COUNT", "Điều chỉnh sau kiểm kê", null));

            assertThat(response.documentCode()).matches("^ADJ-\\d{8}-[0-9A-F]{8}$");
            assertThat(response.documentType()).isEqualTo(InventoryDocumentType.ADJUSTMENT_OUT);
            assertThat(response.status()).isEqualTo(InventoryDocumentStatus.DRAFT);
            assertThat(response.referenceType()).isEqualTo(InventoryReferenceType.STOCK_COUNT);
            assertThat(response.referenceId()).isEqualTo(COUNT_ID.toString());
            assertThat(response.lines()).extracting(AdjustmentLineResponse::adjustmentQuantity)
                    .containsExactly(new BigDecimal("-2.00"), new BigDecimal("1.00"));
            assertThat(count.getAdjustmentDocument()).isNotNull();
            InventoryDocumentLine batchLine = count.getAdjustmentDocument().getLines().get(0);
            assertThat(batchLine.getBatchAllocations()).singleElement()
                    .extracting(InventoryDocumentBatchAllocation::getQuantity)
                    .isEqualTo(new BigDecimal("-2.00"));
            verify(countRepository).save(count);
        }

        @Test
        @DisplayName("create standalone with positive quantity creates a manual increase")
        void create_WhenStandaloneIncrease_ShouldBuildManualAdjustmentIn() {
            AdjustmentResponse response = service.create(standalone("5.00"));

            assertThat(response.documentType()).isEqualTo(InventoryDocumentType.ADJUSTMENT_IN);
            assertThat(response.referenceType()).isEqualTo(InventoryReferenceType.MANUAL);
            assertThat(response.referenceId()).isNull();
            assertThat(response.lines()).singleElement().satisfies(line -> {
                assertThat(line.lineNo()).isEqualTo(1);
                assertThat(line.note()).isEqualTo("Hàng tặng kèm nhập bổ sung");
            });
        }

        @Test
        @DisplayName("create standalone with net zero quantity is classified as an increase")
        void create_WhenNetZero_ShouldBeAdjustmentIn() {
            AdjustmentResponse response = service.create(new CreateAdjustmentRequest(null, BRANCH_ID, "COUNT", null, List.of(
                    new AdjustmentLineRequest(ITEM_ID, LOCATION_ID, new BigDecimal("2.00"), null),
                    new AdjustmentLineRequest(ITEM_ID, LOCATION_ID, new BigDecimal("-2.00"), null))));

            assertThat(response.documentType()).isEqualTo(InventoryDocumentType.ADJUSTMENT_IN);
            assertThat(response.lines()).extracting(AdjustmentLineResponse::lineNo).containsExactly(1, 2);
        }

        @Test
        @DisplayName("create from a count not yet approved throws BadRequestException")
        void create_WhenCountNotApproved_ShouldThrow() {
            InventoryCount count = approvedCount();
            count.setStatus(InventoryCountStatus.PENDING_APPROVAL);
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(COUNT_ID, BRANCH_ID, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Phiên kiểm kê phải được phê duyệt trước khi tạo điều chỉnh");
            verify(adjustmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("create from a count that already has an adjustment throws BadRequestException")
        void create_WhenCountAlreadyAdjusted_ShouldThrow() {
            InventoryCount count = approvedCount();
            count.linkAdjustmentDocument(manualDocument("1.00", InventoryDocumentStatus.DRAFT));
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(COUNT_ID, BRANCH_ID, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Phiên kiểm kê đã có phiếu điều chỉnh");
        }

        @Test
        @DisplayName("create from a count without variance throws BadRequestException")
        void create_WhenCountHasNoVariance_ShouldThrow() {
            InventoryCount count = count(InventoryCountStatus.APPROVED);
            count.addLine(countLine(null, "10.00", "10.00"));
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(COUNT_ID, BRANCH_ID, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không có chênh lệch cần điều chỉnh");
        }

        @Test
        @DisplayName("create from unknown count throws ResourceNotFoundException")
        void create_WhenCountMissing_ShouldThrow() {
            when(countRepository.findByIdAndIsDeletedFalse(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(UNKNOWN_ID, BRANCH_ID, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Inventory count not found: " + UNKNOWN_ID);
        }

        @Test
        @DisplayName("create standalone without lines throws BadRequestException")
        void create_WhenStandaloneWithoutLines_ShouldThrow() {
            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(null, BRANCH_ID, null, null, List.of())))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Danh sách dòng điều chỉnh không được rỗng");
        }

        @Test
        @DisplayName("create standalone with unknown item throws ResourceNotFoundException")
        void create_WhenItemMissing_ShouldThrow() {
            when(itemRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(new CreateAdjustmentRequest(null, BRANCH_ID, null, null,
                    List.of(new AdjustmentLineRequest(UNKNOWN_ID, LOCATION_ID, BigDecimal.ONE, null)))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Inventory item not found: " + UNKNOWN_ID);
        }

        @Test
        @DisplayName("create standalone with inactive item throws BadRequestException")
        void create_WhenItemInactive_ShouldThrow() {
            item.setStatus(InventoryStatus.INACTIVE);

            assertThatThrownBy(() -> service.create(standalone("5.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Mặt hàng không hoạt động: " + ITEM_ID);
        }

        @Test
        @DisplayName("create standalone with inactive location throws BadRequestException")
        void create_WhenLocationInactive_ShouldThrow() {
            location.setStatus(InventoryStatus.INACTIVE);

            assertThatThrownBy(() -> service.create(standalone("5.00")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Vị trí không hoạt động: " + LOCATION_ID);
        }

        @Test
        @DisplayName("create by sale staff throws AccessDeniedException")
        void create_WhenSaleStaff_ShouldDeny() {
            authenticate(STAFF_ID.toString(), "SALE_STAFF");

            assertThatThrownBy(() -> service.create(standalone("5.00")))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền thực hiện thao tác điều chỉnh này");
        }
    }

    @Nested
    @DisplayName("approve")
    class ApproveTests {

        @Test
        @DisplayName("approve records the approving manager")
        void approve_WhenDraft_ShouldApprove() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.DRAFT));

            AdjustmentResponse response = service.approve(DOCUMENT_ID);

            assertThat(response.status()).isEqualTo(InventoryDocumentStatus.APPROVED);
            assertThat(response.approvedBy()).isEqualTo(MANAGER_ID);
            assertThat(response.approvedAt()).isNotNull();
        }

        @Test
        @DisplayName("approve an approved document throws BadRequestException")
        void approve_WhenAlreadyApproved_ShouldThrow() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.APPROVED));

            assertThatThrownBy(() -> service.approve(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ có thể phê duyệt phiếu ở trạng thái DRAFT");
        }

        @Test
        @DisplayName("approve by inventory staff throws AccessDeniedException")
        void approve_WhenInventoryStaff_ShouldDeny() {
            authenticate(STAFF_ID.toString(), "INVENTORY_STAFF");

            assertThatThrownBy(() -> service.approve(DOCUMENT_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("post")
    class PostTests {

        @Test
        @DisplayName("post an approved increase updates the balance and writes ADJUSTMENT_IN")
        void post_WhenApprovedIncrease_ShouldUpdateBalance() {
            authenticate(STAFF_ID.toString(), "INVENTORY_STAFF");
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.APPROVED));
            InventoryBalance balance = balance(null, "10.00");
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.of(balance));

            AdjustmentResponse response = service.post(DOCUMENT_ID);

            assertThat(response.status()).isEqualTo(InventoryDocumentStatus.POSTED);
            assertThat(response.postedAt()).isNotNull();
            assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("15.00");
            InventoryTransaction transaction = capturedTransaction();
            assertThat(transaction.getType()).isEqualTo(InventoryTransactionType.ADJUSTMENT_IN);
            assertThat(transaction.getTransactionCode()).matches("^ADJT-\\d{8}-[0-9A-F]{8}$");
            assertThat(transaction.getReferenceId()).isEqualTo(DOCUMENT_ID.toString());
            InventoryTransactionLine line = transaction.getLines().get(0);
            assertThat(line.getBeforeQuantity()).isEqualByComparingTo("10.00");
            assertThat(line.getAfterQuantity()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("post a stock-count decrease updates item and batch balances and marks the count posted")
        void post_WhenStockCountDecrease_ShouldUpdateBatchAndMarkCountPosted() {
            InventoryAdjustment document = InventoryAdjustment.forNetQuantity(new BigDecimal("-2.00"));
            document.setId(DOCUMENT_ID);
            document.setStatus(InventoryDocumentStatus.APPROVED);
            document.setReferenceType(InventoryReferenceType.STOCK_COUNT);
            document.setReferenceId(COUNT_ID.toString());
            InventoryDocumentLine docLine = documentLine("-2.00");
            InventoryDocumentBatchAllocation allocation = new InventoryDocumentBatchAllocation();
            allocation.setInventoryBatch(batch);
            allocation.setQuantity(new BigDecimal("-2.00"));
            docLine.addBatchAllocation(allocation);
            document.addLine(docLine);
            stubDocument(document);
            InventoryBalance balance = balance(null, "10.00");
            InventoryBalance batchBalance = balance(batch, "6.00");
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.of(balance));
            when(balanceRepository.findForUpdateByBatchId(BATCH_ID, LOCATION_ID)).thenReturn(Optional.of(batchBalance));
            InventoryCount count = count(InventoryCountStatus.APPROVED);
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            service.post(DOCUMENT_ID);

            assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("8.00");
            assertThat(batchBalance.getQuantityOnHand()).isEqualByComparingTo("4.00");
            assertThat(capturedTransaction().getType()).isEqualTo(InventoryTransactionType.ADJUSTMENT_OUT);
            assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.POSTED);
            assertThat(docLine.getReceivedQuantity()).isEqualByComparingTo("-2.00");
        }

        @Test
        @DisplayName("post a decrease to exactly zero stock is allowed")
        void post_WhenDecreaseToZero_ShouldPost() {
            stubDocument(manualDocument("-10.00", InventoryDocumentStatus.APPROVED));
            InventoryBalance balance = balance(null, "10.00");
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.of(balance));

            assertThat(service.post(DOCUMENT_ID).status()).isEqualTo(InventoryDocumentStatus.POSTED);
            assertThat(balance.getQuantityOnHand()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("post a decrease below zero stock throws BadRequestException")
        void post_WhenDecreaseBelowZero_ShouldThrow() {
            stubDocument(manualDocument("-11.00", InventoryDocumentStatus.APPROVED));
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.of(balance(null, "10.00")));

            assertThatThrownBy(() -> service.post(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Tồn kho không đủ để điều chỉnh giảm cho mặt hàng " + ITEM_ID);
            verify(transactionRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("post a batch decrease below zero throws BadRequestException")
        void post_WhenBatchBelowZero_ShouldThrow() {
            InventoryAdjustment document = manualDocument("-2.00", InventoryDocumentStatus.APPROVED);
            InventoryDocumentBatchAllocation allocation = new InventoryDocumentBatchAllocation();
            allocation.setInventoryBatch(batch);
            allocation.setQuantity(new BigDecimal("-2.00"));
            document.getLines().get(0).addBatchAllocation(allocation);
            stubDocument(document);
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.of(balance(null, "10.00")));
            when(balanceRepository.findForUpdateByBatchId(BATCH_ID, LOCATION_ID)).thenReturn(Optional.of(balance(batch, "1.00")));

            assertThatThrownBy(() -> service.post(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Tồn kho lô không đủ để điều chỉnh giảm: LOT-20261103-001");
        }

        @Test
        @DisplayName("post without a balance row throws BadRequestException")
        void post_WhenBalanceMissing_ShouldThrow() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.APPROVED));
            when(balanceRepository.findForUpdate(ITEM_ID, LOCATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.post(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không tìm thấy tồn kho cho mặt hàng " + ITEM_ID + " tại vị trí " + LOCATION_ID);
        }

        @Test
        @DisplayName("post a draft document throws BadRequestException")
        void post_WhenDraft_ShouldThrow() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.DRAFT));

            assertThatThrownBy(() -> service.post(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ có thể hạch toán phiếu đã được phê duyệt");
        }
    }

    @Nested
    @DisplayName("reject")
    class RejectTests {

        @Test
        @DisplayName("reject a manual draft marks it REJECTED")
        void reject_WhenManualDraft_ShouldReject() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.DRAFT));

            assertThat(service.reject(DOCUMENT_ID).status()).isEqualTo(InventoryDocumentStatus.REJECTED);
            verify(countRepository, never()).findByIdAndIsDeletedFalse(any());
        }

        @Test
        @DisplayName("reject a stock-count adjustment sends the count back to reconciliation")
        void reject_WhenStockCountAdjustment_ShouldReturnCountToReconciling() {
            InventoryAdjustment document = manualDocument("-1.00", InventoryDocumentStatus.APPROVED);
            document.setReferenceType(InventoryReferenceType.STOCK_COUNT);
            document.setReferenceId(COUNT_ID.toString());
            stubDocument(document);
            InventoryCount count = count(InventoryCountStatus.APPROVED);
            count.linkAdjustmentDocument(document);
            when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));

            service.reject(DOCUMENT_ID);

            assertThat(count.getStatus()).isEqualTo(InventoryCountStatus.RECONCILING);
            assertThat(count.getAdjustmentDocument()).isNull();
        }

        @Test
        @DisplayName("reject a posted document throws BadRequestException")
        void reject_WhenPosted_ShouldThrow() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.POSTED));

            assertThatThrownBy(() -> service.reject(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không thể từ chối phiếu đã hạch toán");
        }

        @Test
        @DisplayName("reject an already rejected document throws BadRequestException")
        void reject_WhenAlreadyRejected_ShouldThrow() {
            stubDocument(manualDocument("5.00", InventoryDocumentStatus.REJECTED));

            assertThatThrownBy(() -> service.reject(DOCUMENT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Phiếu đã bị từ chối");
        }
    }

    // ====== Fixtures ======

    private void authenticate(String userId, String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(userId, "kho-test", "test", Set.of(role), "corr-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void stubDocument(InventoryAdjustment document) {
        when(adjustmentRepository.findByIdAndIsDeletedFalse(DOCUMENT_ID)).thenReturn(Optional.of(document));
    }

    private InventoryTransaction capturedTransaction() {
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private InventoryAdjustment manualDocument(String quantity, InventoryDocumentStatus status) {
        InventoryAdjustment document = InventoryAdjustment.forNetQuantity(new BigDecimal(quantity));
        document.setId(DOCUMENT_ID);
        document.setDocumentCode("ADJ-20261103-9F8E7D6C");
        document.setBranchId(BRANCH_ID);
        document.setStatus(status);
        document.setReferenceType(InventoryReferenceType.MANUAL);
        document.addLine(documentLine(quantity));
        return document;
    }

    private InventoryDocumentLine documentLine(String quantity) {
        InventoryDocumentLine line = new InventoryDocumentLine();
        line.setLineNo(1);
        line.setInventoryItem(item);
        line.setSourceLocation(location);
        line.setOrderedQuantity(new BigDecimal(quantity));
        return line;
    }

    private InventoryCount count(InventoryCountStatus status) {
        InventoryCount count = new InventoryCount();
        count.setId(COUNT_ID);
        count.setBranchId(BRANCH_ID);
        count.setScopeType(InventoryCountScopeType.FULL);
        count.setStatus(status);
        return count;
    }

    private InventoryCount approvedCount() {
        InventoryCount count = count(InventoryCountStatus.APPROVED);
        count.addLine(countLine(batch, "6.00", "4.00"));
        count.addLine(countLine(null, "10.00", "11.00"));
        count.addLine(countLine(null, "3.00", "3.00"));
        return count;
    }

    private InventoryCountLine countLine(InventoryBatch lineBatch, String snapshot, String counted) {
        InventoryCountLine line = new InventoryCountLine();
        line.setInventoryItem(item);
        line.setLocation(location);
        line.setInventoryBatch(lineBatch);
        line.takeSnapshot(new BigDecimal(snapshot));
        line.recordCount(new BigDecimal(counted), "COUNT", null);
        return line;
    }

    private InventoryBalance balance(InventoryBatch balanceBatch, String quantity) {
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(balanceBatch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        return balance;
    }

    private CreateAdjustmentRequest standalone(String quantity) {
        return new CreateAdjustmentRequest(null, BRANCH_ID, "GIFT", "Nhập bổ sung hàng tặng",
                List.of(new AdjustmentLineRequest(ITEM_ID, LOCATION_ID, new BigDecimal(quantity), "Hàng tặng kèm nhập bổ sung")));
    }
}
