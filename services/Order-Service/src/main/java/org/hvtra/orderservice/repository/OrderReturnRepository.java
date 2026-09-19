package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnReportBaseRow;
import org.hvtra.orderservice.dto.response.OrderReturnReportCoreMetricsResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnStatusBreakdownResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    boolean existsByReturnCode(String returnCode);

    @Query(
            value = """
                    select new org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse(
                        orderReturn.id,
                        orderReturn.returnCode,
                        orders.id,
                        orders.orderCode,
                        orderReturn.status,
                        orderReturn.createdAt,
                        orderReturn.updatedAt,
                        count(items.id),
                        orders.customerNameSnapshot,
                        orders.customerPhoneSnapshot,
                        orderReturn.refundRequired
                    )
                    from OrderReturn orderReturn
                    join orderReturn.order orders
                    left join orderReturn.items items on items.isDeleted = false
                    where orderReturn.isDeleted = false
                      and orders.isDeleted = false
                      and (:status is null or orderReturn.status = :status)
                      and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
                      and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
                      and (
                          :keywordPattern is null
                          or lower(orderReturn.returnCode) like :keywordPattern
                          or lower(orders.orderCode) like :keywordPattern
                          or lower(orders.customerNameSnapshot) like :keywordPattern
                          or lower(orders.customerPhoneSnapshot) like :keywordPattern
                      )
                    group by
                        orderReturn.id,
                        orderReturn.returnCode,
                        orders.id,
                        orders.orderCode,
                        orderReturn.status,
                        orderReturn.createdAt,
                        orderReturn.updatedAt,
                        orders.customerNameSnapshot,
                        orders.customerPhoneSnapshot,
                        orderReturn.refundRequired
                    """,
            countQuery = """
                    select count(orderReturn.id)
                    from OrderReturn orderReturn
                    join orderReturn.order orders
                    where orderReturn.isDeleted = false
                      and orders.isDeleted = false
                      and (:status is null or orderReturn.status = :status)
                      and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
                      and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
                      and (
                          :keywordPattern is null
                          or lower(orderReturn.returnCode) like :keywordPattern
                          or lower(orders.orderCode) like :keywordPattern
                          or lower(orders.customerNameSnapshot) like :keywordPattern
                          or lower(orders.customerPhoneSnapshot) like :keywordPattern
                      )
                    """
    )
    Page<OrderReturnSummaryResponse> findReturnSummaries(
            @Param("keywordPattern") String keywordPattern,
            @Param("status") ReturnStatus status,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore,
            Pageable pageable
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse(
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orderReturn.status,
                orderReturn.createdAt,
                orderReturn.updatedAt,
                count(items.id),
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.refundRequired
            )
            from OrderReturn orderReturn
            join orderReturn.order orders
            left join orderReturn.items items on items.isDeleted = false
            where orderReturn.isDeleted = false
              and orders.isDeleted = false
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
              and (
                  :keywordPattern is null
                  or lower(orderReturn.returnCode) like :keywordPattern
                  or lower(orders.orderCode) like :keywordPattern
                  or lower(orders.customerNameSnapshot) like :keywordPattern
                  or lower(orders.customerPhoneSnapshot) like :keywordPattern
              )
            group by
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orderReturn.status,
                orderReturn.createdAt,
                orderReturn.updatedAt,
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.refundRequired
            order by orderReturn.createdAt desc, orderReturn.id desc
            """)
    List<OrderReturnSummaryResponse> findRefundReturnSummaries(
            @Param("keywordPattern") String keywordPattern,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse(
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orderReturn.status,
                orderReturn.createdAt,
                orderReturn.updatedAt,
                count(items.id),
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.refundRequired
            )
            from OrderReturn orderReturn
            join orderReturn.order orders
            left join orderReturn.items items on items.isDeleted = false
            where orderReturn.isDeleted = false
              and orders.isDeleted = false
              and (:status is null or orderReturn.status = :status)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
              and (
                  :keywordPattern is null
                  or lower(orderReturn.returnCode) like :keywordPattern
                  or lower(orders.orderCode) like :keywordPattern
                  or lower(orders.customerNameSnapshot) like :keywordPattern
                  or lower(orders.customerPhoneSnapshot) like :keywordPattern
              )
            group by
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orderReturn.status,
                orderReturn.createdAt,
                orderReturn.updatedAt,
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.refundRequired
            order by orderReturn.createdAt desc, orderReturn.id desc
            """)
    List<OrderReturnSummaryResponse> findReturnSummariesList(
            @Param("keywordPattern") String keywordPattern,
            @Param("status") ReturnStatus status,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnReportBaseRow(
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orders.branchId,
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.status,
                orderReturn.createdAt,
                count(items.id)
            )
            from OrderReturn orderReturn
            join orderReturn.order orders
            left join orderReturn.items items on items.isDeleted = false
            where orderReturn.isDeleted = false
              and orders.isDeleted = false
              and (:branchId is null or orders.branchId = :branchId)
              and (:status is null or orderReturn.status = :status)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
              and (
                  :keywordPattern is null
                  or lower(orderReturn.returnCode) like :keywordPattern
                  or lower(orders.orderCode) like :keywordPattern
                  or lower(orders.customerNameSnapshot) like :keywordPattern
                  or lower(orders.customerPhoneSnapshot) like :keywordPattern
              )
            group by
                orderReturn.id,
                orderReturn.returnCode,
                orders.id,
                orders.orderCode,
                orders.branchId,
                orders.customerNameSnapshot,
                orders.customerPhoneSnapshot,
                orderReturn.status,
                orderReturn.createdAt
            order by orderReturn.createdAt desc, orderReturn.id desc
            """)
    List<OrderReturnReportBaseRow> findReportBaseRows(
            @Param("keywordPattern") String keywordPattern,
            @Param("status") ReturnStatus status,
            @Param("branchId") UUID branchId,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnReportCoreMetricsResponse(
                count(orderReturn.id),
                coalesce(sum(case when orderReturn.status = :completedStatus then 1 else 0 end), 0),
                coalesce(sum(case when orderReturn.status in :processingStatuses then 1 else 0 end), 0),
                coalesce(sum(case when orderReturn.status = :cancelledStatus then 1 else 0 end), 0),
                count(distinct orders.id)
            )
            from OrderReturn orderReturn
            join orderReturn.order orders
            where orderReturn.isDeleted = false
              and orders.isDeleted = false
              and (:branchId is null or orders.branchId = :branchId)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
            """)
    OrderReturnReportCoreMetricsResponse findReportCoreMetrics(
            @Param("branchId") UUID branchId,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore,
            @Param("completedStatus") ReturnStatus completedStatus,
            @Param("cancelledStatus") ReturnStatus cancelledStatus,
            @Param("processingStatuses") List<ReturnStatus> processingStatuses
    );

    @Query("""
            select new org.hvtra.orderservice.dto.response.OrderReturnStatusBreakdownResponse(
                orderReturn.status,
                count(orderReturn.id)
            )
            from OrderReturn orderReturn
            join orderReturn.order orders
            where orderReturn.isDeleted = false
              and orders.isDeleted = false
              and (:branchId is null or orders.branchId = :branchId)
              and (:fromCreatedAt is null or orderReturn.createdAt >= :fromCreatedAt)
              and (:toCreatedBefore is null or orderReturn.createdAt < :toCreatedBefore)
            group by orderReturn.status
            """)
    List<OrderReturnStatusBreakdownResponse> findReportStatusBreakdown(
            @Param("branchId") UUID branchId,
            @Param("fromCreatedAt") Instant fromCreatedAt,
            @Param("toCreatedBefore") Instant toCreatedBefore
    );

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
            where orderReturn.order.id in :orderIds
              and orderReturn.isDeleted = false
            order by orderReturn.createdAt desc
            """)
    List<OrderReturn> findByOrderIdInAndIsDeletedFalseWithItems(@Param("orderIds") Collection<UUID> orderIds);

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

    @Query("""
            select distinct orderReturn
            from OrderReturn orderReturn
            left join fetch orderReturn.items items
            left join fetch items.orderItem orderItem
            left join fetch orderReturn.order orders
            where orderReturn.id in :ids
              and orderReturn.isDeleted = false
            """)
    List<OrderReturn> findByIdInAndIsDeletedFalseWithItems(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct orderReturn
            from OrderReturn orderReturn
            left join fetch orderReturn.items items
            left join fetch items.orderItem
            left join fetch orderReturn.order
            where orderReturn.id = :id
              and orderReturn.isDeleted = false
            """)
    Optional<OrderReturn> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);
}
