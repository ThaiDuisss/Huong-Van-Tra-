package org.hvtra.iventoryservice.availability.repository;

import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Các truy vấn chỉ đọc phục vụ ước tính ngày có hàng. */
public interface AvailabilityQueryRepository extends Repository<ProductionOrder, UUID> {

    /** Lệnh sản xuất còn mở của một mặt hàng, cũ trước. */
    @Query("""
            select o from ProductionOrder o
            where o.productVariant.referenceId = :variantId and o.isDeleted = false and o.status in :statuses
            order by o.createdAt asc
            """)
    List<ProductionOrder> findOpenProductionOrders(
            @Param("variantId") UUID variantId,
            @Param("statuses") Collection<ProductionOrderStatus> statuses);

    /**
     * Lệnh sản xuất đã hoàn tất gần nhất của một mặt hàng (để tính lại thời gian sản xuất trung bình).
     * Lệnh được duyệt là hoàn tất: thành phẩm nhập kho ngay lúc duyệt.
     */
    @Query("""
            select o from ProductionOrder o
            where o.productVariant.referenceId = :variantId and o.isDeleted = false
              and o.status = org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus.APPROVED
              and o.approvedAt is not null
            order by o.approvedAt desc
            """)
    List<ProductionOrder> findRecentCompletedByVariant(@Param("variantId") UUID variantId, Pageable pageable);

    /**
     * Trung bình thời gian có hàng (giây) đã lưu sẵn trên các mặt hàng cùng loại; dùng khi chính mặt hàng đang
     * hỏi chưa có lịch sử. Null nếu chưa mặt hàng nào có lịch sử.
     */
    @Query("""
            select avg(i.avgSupplyLeadSeconds) from InventoryItem i
            where i.itemType = :itemType and i.isDeleted = false and i.avgSupplyLeadSeconds is not null
            """)
    Double averageSupplyLeadSeconds(@Param("itemType") InventoryItemType itemType);

    /**
     * Lệnh sản xuất còn dang dở đã gắn với một đơn hàng cho một mặt hàng. Lệnh đã hoàn tất không tính: hàng
     * làm ra đã vào tồn, nếu vẫn thiếu (bị bán cho khách khác) thì phải tạo lệnh mới.
     */
    @Query("""
            select o from ProductionOrder o
            where o.sourceOrderId = :orderId and o.productVariant.referenceId = :variantId and o.isDeleted = false
              and o.status not in (org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus.CANCELLED,
                                   org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus.REJECTED,
                                   org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus.APPROVED)
            order by o.createdAt asc
            """)
    List<ProductionOrder> findBySourceOrderAndVariant(@Param("orderId") UUID orderId, @Param("variantId") UUID variantId);

    /** Các dòng phiếu nhập kho còn đang trên đường về của những nguyên liệu này. */
    @Query("""
            select l from InventoryDocumentLine l
            join fetch l.document d
            join fetch l.inventoryItem i
            where i.id in :itemIds and d.isDeleted = false
              and d.documentType = org.hvtra.iventoryservice.domain.enums.InventoryDocumentType.GOODS_RECEIPT
              and d.status in :statuses
            """)
    List<InventoryDocumentLine> findOpenReceiptLines(
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("statuses") Collection<InventoryDocumentStatus> statuses);

    /** Phiếu nhập đã ghi sổ gần nhất có chứa nguyên liệu này (để tính lại thời gian nhập trung bình). */
    @Query("""
            select d from InventoryDocument d
            where d.isDeleted = false
              and d.documentType = org.hvtra.iventoryservice.domain.enums.InventoryDocumentType.GOODS_RECEIPT
              and d.status = org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus.POSTED
              and d.postedAt is not null
              and exists (select 1 from InventoryDocumentLine l where l.document = d and l.inventoryItem.id = :itemId)
            order by d.postedAt desc
            """)
    List<InventoryDocument> findRecentPostedReceiptsByItem(@Param("itemId") UUID itemId, Pageable pageable);
}
