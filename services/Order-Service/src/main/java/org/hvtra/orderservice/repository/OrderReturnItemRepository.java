package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, UUID> {

    @Query("""
            select sum(item.quantity)
            from OrderReturnItem item
            where item.orderItem.id = :orderItemId
              and item.isDeleted = false
              and item.orderReturn.isDeleted = false
              and item.orderReturn.status in :statuses
            """)
    BigDecimal sumReturnedQuantity(
            @Param("orderItemId") UUID orderItemId,
            @Param("statuses") Collection<ReturnStatus> statuses
    );
}
