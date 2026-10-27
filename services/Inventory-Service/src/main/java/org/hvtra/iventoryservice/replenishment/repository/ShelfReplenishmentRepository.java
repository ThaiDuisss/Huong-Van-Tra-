package org.hvtra.iventoryservice.replenishment.repository;

import org.hvtra.iventoryservice.domain.entity.document.ShelfReplenishment;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn trên {@link ShelfReplenishment} luôn tự lọc document_type = TRANSFER_RECEIPT. */
@Repository
public interface ShelfReplenishmentRepository
        extends JpaRepository<ShelfReplenishment, UUID>, JpaSpecificationExecutor<ShelfReplenishment> {

    Optional<ShelfReplenishment> findByIdAndIsDeletedFalse(UUID id);

    /** Phiếu còn mở gắn với cùng một tham chiếu (vd. đơn POS), để gộp các dòng thiếu vào một phiếu. */
    Optional<ShelfReplenishment> findFirstByReferenceIdAndStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
            String referenceId, Collection<InventoryDocumentStatus> statuses);

    long countByStatusAndIsDeletedFalse(InventoryDocumentStatus status);

    long countByStatusAndApprovedAtIsNullAndIsDeletedFalse(InventoryDocumentStatus status);
}
