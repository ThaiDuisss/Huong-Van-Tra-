package org.hvtra.orderservice.repository;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByIdAndIsDeletedFalse(UUID id);
    Optional<Order> findByClientOrderIdAndIsDeletedFalse(UUID clientOrderId);

    @Query("""
            select distinct orders
            from Order orders
            left join fetch orders.items
            where orders.id = :orderId
              and orders.isDeleted = false
            """)
    Optional<Order> findByIdAndIsDeletedFalseWithItems(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct orders
            from Order orders
            left join fetch orders.items
            where orders.id = :orderId
              and orders.isDeleted = false
            """)
    Optional<Order> findByIdAndIsDeletedFalseWithItemsForUpdate(@Param("orderId") UUID orderId);

    @Query("""
            select distinct orders
            from Order orders
            left join fetch orders.items
            where orders.id in :orderIds
              and orders.isDeleted = false
            """)
    List<Order> findAllWithItemsByIdIn(@Param("orderIds") List<UUID> orderIds);

    boolean existsByOrderCode(String orderCode);

    /** Đơn đã thanh toán nhưng trừ kho thất bại (không phải đơn chờ hàng): scheduler thử lại. */
    @Query("""
            select o from Order o
            where o.status = org.hvtra.orderservice.domain.enums.OrderStatus.PAYMENT_CONFIRMED
              and o.paymentStatus = org.hvtra.orderservice.domain.enums.PaymentStatus.PAID
              and o.inventoryProcessingStatus = org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus.FAILED
              and o.backorder = false and o.isDeleted = false
            order by o.createdAt asc
            """)
    List<Order> findPaidOrdersWithFailedInventory();
    List<Order> findByStatusAndExpiresAtBeforeAndIsDeletedFalse(OrderStatus status, Instant expiresAt);
}
