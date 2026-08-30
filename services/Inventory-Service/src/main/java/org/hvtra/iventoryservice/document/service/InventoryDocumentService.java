package org.hvtra.iventoryservice.document.service;

import org.hvtra.iventoryservice.document.dto.request.ApproveReceiptRequest;
import org.hvtra.iventoryservice.document.dto.request.CreatePurchaseReceiptRequest;
import org.hvtra.iventoryservice.document.dto.request.QcReceiptRequest;
import org.hvtra.iventoryservice.document.dto.request.ReceiveReceiptRequest;
import org.hvtra.iventoryservice.document.dto.request.RejectReceiptRequest;
import org.hvtra.iventoryservice.document.dto.request.UpdatePurchaseReceiptRequest;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface InventoryDocumentService {

    PageResponse<InventoryDocumentResponse> findAll(
            InventoryDocumentType documentType,
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    );

    InventoryDocumentResponse findById(UUID id);

    InventoryDocumentResponse createPurchaseReceipt(CreatePurchaseReceiptRequest request);

    InventoryDocumentResponse updatePurchaseReceipt(UUID id, UpdatePurchaseReceiptRequest request);

    InventoryDocumentResponse submitForApproval(UUID id);

    InventoryDocumentResponse approve(UUID id, ApproveReceiptRequest request);

    InventoryDocumentResponse reject(UUID id, RejectReceiptRequest request);

    InventoryDocumentResponse startReceiving(UUID id);

    InventoryDocumentResponse confirmReceiving(UUID id, ReceiveReceiptRequest request);

    InventoryDocumentResponse completeQc(UUID id, QcReceiptRequest request);

    InventoryDocumentResponse post(UUID id);

    InventoryDocumentResponse cancel(UUID id, String note);
}
