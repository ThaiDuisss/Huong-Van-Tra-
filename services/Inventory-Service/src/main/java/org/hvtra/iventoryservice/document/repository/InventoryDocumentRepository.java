package org.hvtra.iventoryservice.document.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy vấn dùng chung cho mọi loại chứng từ kho (vd. báo cáo). Lấy / lưu theo từng loại phiếu dùng repository
 * của lớp con (GoodsReceiptRepository, ShelfReplenishmentRepository, InventoryAdjustmentRepository) để
 * không đụng nhầm phiếu loại khác.
 */
@Repository
public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, UUID>, JpaSpecificationExecutor<InventoryDocument> {

    Optional<InventoryDocument> findByDocumentCode(String documentCode);

    boolean existsByDocumentCode(String documentCode);

    @Query("SELECT COUNT(d) FROM InventoryDocument d WHERE d.documentType = :type")
    long countByDocumentType(InventoryDocumentType type);
}
