package org.hvtra.iventoryservice.document.repository.projection;

import java.time.Instant;

public interface InventoryDocumentRow {
    String getId();
    String getDocumentCode();
    String getDocumentType();
    String getStatus();
    String getBranchId();
    String getLocationId();
    String getLocationName();
    String getLocationCode();
    String getSupplierId();
    String getReferenceType();
    String getReferenceId();
    Instant getExpectedDate();
    String getNote();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    String getCreatedBy();
    String getApprovedBy();
    Instant getApprovedAt();
    String getApprovalNote();
    String getRejectedBy();
    Instant getRejectedAt();
    String getRejectionReason();
    String getReceivedBy();
    Instant getReceivedAt();
    String getQcBy();
    Instant getQcAt();
    String getPostedBy();
    Instant getPostedAt();
}
