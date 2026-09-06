package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    boolean existsByReturnCode(String returnCode);

    @Query("""
            select distinct orderReturn
            from OrderReturn orderReturn
            left join fetch orderReturn.items items
            left join fetch items.orderItem
            left join fetch orderReturn.order
            where orderReturn.order.id = :orderId
              and orderReturn.isDeleted = false
            order by orderReturn.createdAt desc
            """)
    List<OrderReturn> findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("orderId") UUID orderId);

    @Query("""
            select distinct orderReturn
            from OrderReturn orderReturn
            left join fetch orderReturn.items items
            left join fetch items.orderItem
            left join fetch orderReturn.order
            where orderReturn.id = :id
              and orderReturn.isDeleted = false
            """)
    Optional<OrderReturn> findByIdAndIsDeletedFalse(@Param("id") UUID id);
}
