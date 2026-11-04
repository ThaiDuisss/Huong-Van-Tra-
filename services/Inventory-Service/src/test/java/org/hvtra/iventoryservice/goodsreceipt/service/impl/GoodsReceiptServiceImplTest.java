package org.hvtra.iventoryservice.goodsreceipt.service.impl;

import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentBatchAllocationRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentLineRepository;
import org.hvtra.iventoryservice.domain.entity.document.GoodsReceipt;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.goodsreceipt.dto.request.ApproveReceiptRequest;
import org.hvtra.iventoryservice.goodsreceipt.dto.request.RejectReceiptRequest;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.goodsreceipt.mapper.GoodsReceiptMapper;
import org.hvtra.iventoryservice.goodsreceipt.repository.GoodsReceiptRepository;
import org.hvtra.iventoryservice.goodsreceipt.repository.projection.GoodsReceiptRow;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceImplTest {

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;

    @Mock
    private InventoryLocationRepository locationRepository;

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryBatchRepository batchRepository;

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private InventoryDocumentLineRepository lineRepository;

    @Mock
    private InventoryDocumentBatchAllocationRepository allocationRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private IdentityClient identityClient;

    @Spy
    private GoodsReceiptMapper mapper = new GoodsReceiptMapper();

    @Mock
    private SupplyLeadTimeService supplyLeadTimeService;

    @InjectMocks
    private GoodsReceiptServiceImpl service;

    private UUID receiptId;
    private GoodsReceipt receipt;

    @BeforeEach
    void setUp() {
        receiptId = UUID.fromString("550e8400-e29b-41d4-a716-446655440080");

        receipt = new GoodsReceipt();
        receipt.setId(receiptId);
        receipt.setDocumentCode("NK-202611-0001");
        receipt.setStatus(InventoryDocumentStatus.DRAFT);
        receipt.setCreatedAt(Instant.now());

        InventoryDocumentLine line = new InventoryDocumentLine();
        line.setId(UUID.randomUUID());
        line.setLineNo(1);
        line.setOrderedQuantity(new BigDecimal("100.00"));
        receipt.setLines(new ArrayList<>(List.of(line)));
    }

    @Test
    @DisplayName("getSummaryCounts - Success aggregating status counts")
    void getSummaryCounts_Success() {
        List<Object[]> rawCounts = new ArrayList<>();
        rawCounts.add(new Object[]{InventoryDocumentStatus.DRAFT, 5L});
        rawCounts.add(new Object[]{InventoryDocumentStatus.APPROVED, 3L});
        rawCounts.add(new Object[]{InventoryDocumentStatus.POSTED, 10L});
        when(goodsReceiptRepository.countGroupByStatus()).thenReturn(rawCounts);

        GoodsReceiptSummaryCountResponse response = service.getSummaryCounts();

        assertThat(response).isNotNull();
        assertThat(response.draft()).isEqualTo(5L);
        assertThat(response.approved()).isEqualTo(3L);
        assertThat(response.posted()).isEqualTo(10L);
        assertThat(response.total()).isEqualTo(18L);
    }

    @Test
    @DisplayName("findById - Success returning receipt projection")
    void findById_Success() {
        GoodsReceiptRow row = mock(GoodsReceiptRow.class);
        when(row.getId()).thenReturn(receiptId.toString());
        when(row.getDocumentCode()).thenReturn("NK-202611-0001");
        when(row.getDocumentType()).thenReturn("GOODS_RECEIPT");
        when(row.getStatus()).thenReturn("DRAFT");
        when(goodsReceiptRepository.findRowById(receiptId)).thenReturn(Optional.of(row));

        GoodsReceiptResponse response = service.findById(receiptId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(receiptId);
        assertThat(response.documentCode()).isEqualTo("NK-202611-0001");
        assertThat(response.status()).isEqualTo(InventoryDocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("findById - Throws ResourceNotFoundException when receipt not found")
    void findById_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(goodsReceiptRepository.findRowById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("GoodsReceipt");
    }

    @Test
    @DisplayName("submitForApproval - Success transitioning DRAFT to PENDING_APPROVAL")
    void submitForApproval_Success() {
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenReturn(receipt);

        GoodsReceiptResponse response = service.submitForApproval(receiptId);

        assertThat(response).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo(InventoryDocumentStatus.PENDING_APPROVAL);
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    @DisplayName("submitForApproval - Throws BadRequestException when not DRAFT")
    void submitForApproval_NotDraft_ThrowsException() {
        receipt.setStatus(InventoryDocumentStatus.APPROVED);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.submitForApproval(receiptId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only DRAFT documents can be submitted");
    }

    @Test
    @DisplayName("submitForApproval - Throws BadRequestException when receipt has no lines")
    void submitForApproval_EmptyLines_ThrowsException() {
        receipt.setLines(new ArrayList<>());
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.submitForApproval(receiptId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot submit a document with no line items");
    }

    @Test
    @DisplayName("approve - Success approving receipt in PENDING_APPROVAL status")
    void approve_Success() {
        receipt.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenReturn(receipt);

        ApproveReceiptRequest request = new ApproveReceiptRequest("Đồng ý nhập lô hàng");
        GoodsReceiptResponse response = service.approve(receiptId, request);

        assertThat(response).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo(InventoryDocumentStatus.APPROVED);
        assertThat(receipt.getApprovalNote()).isEqualTo("Đồng ý nhập lô hàng");
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    @DisplayName("approve - Throws BadRequestException when not PENDING_APPROVAL")
    void approve_NotPendingApproval_ThrowsException() {
        receipt.setStatus(InventoryDocumentStatus.DRAFT);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.approve(receiptId, new ApproveReceiptRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only PENDING_APPROVAL documents can be approved");
    }

    @Test
    @DisplayName("reject - Success rejecting receipt in PENDING_APPROVAL status")
    void reject_Success() {
        receipt.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenReturn(receipt);

        RejectReceiptRequest request = new RejectReceiptRequest("Sai thông tin hợp đồng nhà cung cấp");
        GoodsReceiptResponse response = service.reject(receiptId, request);

        assertThat(response).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo(InventoryDocumentStatus.REJECTED);
        assertThat(receipt.getRejectionReason()).isEqualTo("Sai thông tin hợp đồng nhà cung cấp");
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    @DisplayName("startReceiving - Success transitioning APPROVED to RECEIVING")
    void startReceiving_Success() {
        receipt.setStatus(InventoryDocumentStatus.APPROVED);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenReturn(receipt);

        GoodsReceiptResponse response = service.startReceiving(receiptId);

        assertThat(response).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo(InventoryDocumentStatus.RECEIVING);
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    @DisplayName("cancel - Success cancelling document")
    void cancel_Success() {
        receipt.setStatus(InventoryDocumentStatus.DRAFT);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenReturn(receipt);

        GoodsReceiptResponse response = service.cancel(receiptId, "Nhà cung cấp hết hàng");

        assertThat(response).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo(InventoryDocumentStatus.CANCELLED);
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    @DisplayName("cancel - Throws BadRequestException when cancelling POSTED document")
    void cancel_Posted_ThrowsException() {
        receipt.setStatus(InventoryDocumentStatus.POSTED);
        when(goodsReceiptRepository.findByIdWithDetails(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancel(receiptId, "Thử hủy phiếu đã post"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot cancel an already POSTED document");
    }
}
