package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnReportReasonBreakdownResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    @Query("""
            select coalesce(sum(item.quantity), 0)
            from OrderReturnItem item
            join item.orderReturn orderReturn
            join orderReturn.order orders
            where item.isDeleted = false
              and orderReturn.isDeleted = false
              and orders.isDeleted = false
              and orderReturn.status in :includedStatuses
              and (:branchId is null or orders.branchId = :branchId)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
            """)
    BigDecimal sumReportReturnedQuantity(
            @Param("branchId") UUID branchId,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore,
            @Param("includedStatuses") Collection<ReturnStatus> includedStatuses
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnReportReasonBreakdownResponse(
                item.reason,
                count(item.id),
                coalesce(sum(item.quantity), 0)
            )
            from OrderReturnItem item
            join item.orderReturn orderReturn
            join orderReturn.order orders
            where item.isDeleted = false
              and orderReturn.isDeleted = false
              and orders.isDeleted = false
              and orderReturn.status <> :cancelledStatus
              and (:branchId is null or orders.branchId = :branchId)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
            group by item.reason
            """)
    List<OrderReturnReportReasonBreakdownResponse> findReportReasonBreakdown(
            @Param("branchId") UUID branchId,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore,
            @Param("cancelledStatus") ReturnStatus cancelledStatus
    );
}
