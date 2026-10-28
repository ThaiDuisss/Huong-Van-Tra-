package org.hvtra.iventoryservice.production.repository;

import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionOrderRepository
        extends JpaRepository<ProductionOrder, UUID>, JpaSpecificationExecutor<ProductionOrder> {

    boolean existsByOrderCode(String orderCode);

    /**
     * Chỉ fetch "lines" (1 bag). KHÔNG fetch kèm "lines.allocations" ở đây — join 2 collection dạng
     * List (bag) cùng lúc bị Hibernate ném MultipleBagFetchException. "allocations" của từng dòng vẫn
     * lazy-load bình thường khi cần (đã có @BatchSize trên ProductionOrderLine.allocations để Hibernate
     * gộp nhiều lần lazy-load đó thành 1 câu "IN (...)" thay vì N query riêng lẻ).
     */
    @Query("SELECT DISTINCT o FROM ProductionOrder o LEFT JOIN FETCH o.lines WHERE o.id = :id")
    Optional<ProductionOrder> findByIdWithLines(UUID id);

    /** Lệnh đã duyệt trong kỳ kèm dòng nguyên liệu — dùng cho báo cáo sản xuất. */
    @Query("""
            SELECT DISTINCT o FROM ProductionOrder o
            JOIN FETCH o.productVariant
            LEFT JOIN FETCH o.lines l
            LEFT JOIN FETCH l.ingredient
            WHERE o.isDeleted = false
              AND o.status = org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus.APPROVED
              AND o.approvedAt >= :from AND o.approvedAt < :to
            """)
    List<ProductionOrder> findApprovedWithLinesBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT DISTINCT o FROM ProductionOrder o
            LEFT JOIN FETCH o.lines l
            LEFT JOIN FETCH l.ingredient
            WHERE o.isDeleted = false AND o.status IN :statuses
            """)
    List<ProductionOrder> findWithLinesByStatusIn(@Param("statuses") Collection<ProductionOrderStatus> statuses);
}
