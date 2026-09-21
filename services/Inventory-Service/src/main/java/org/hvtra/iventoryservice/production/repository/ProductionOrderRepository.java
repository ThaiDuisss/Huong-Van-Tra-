package org.hvtra.iventoryservice.production.repository;

import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
