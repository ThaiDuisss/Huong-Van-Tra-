package org.hvtra.orderservice.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from OrderItem item
            where item.order.id = :orderId
              and item.id in :itemIds
              and item.isDeleted = false
            order by item.id
            """)
    List<OrderItem> findReturnableItemsForUpdate(
            @Param("orderId") UUID orderId,
            @Param("itemIds") Collection<UUID> itemIds
    );
}
