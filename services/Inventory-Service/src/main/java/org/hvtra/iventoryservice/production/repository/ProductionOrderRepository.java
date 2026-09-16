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

    @Query("SELECT DISTINCT o FROM ProductionOrder o LEFT JOIN FETCH o.lines l LEFT JOIN FETCH l.allocations WHERE o.id = :id")
    Optional<ProductionOrder> findByIdWithDetails(UUID id);
}
