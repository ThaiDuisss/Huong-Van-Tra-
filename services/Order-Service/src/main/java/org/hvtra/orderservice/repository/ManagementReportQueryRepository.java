package org.hvtra.orderservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class ManagementReportQueryRepository {

    private static final List<OrderStatus> SALE_ORDER_STATUSES =
            OrderStatisticReportQueryRepository.SALE_ORDER_STATUSES;
    private static final List<PaymentStatus> PAID_PAYMENT_STATUSES =
            OrderStatisticReportQueryRepository.PAID_PAYMENT_STATUSES;

    @PersistenceContext
    private EntityManager entityManager;

    public List<OrderSummaryRow> findOrderSummaries(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            UUID customerId,
            Collection<UUID> variantIds
    ) {
        StringBuilder jpql = new StringBuilder("""
                select
                    orders.id,
                    orders.createdAt,
                    orders.finalAmount
                from Order orders
                where orders.isDeleted = false
                  and orders.status in :saleStatuses
                  and orders.paymentStatus in :paidStatuses
                """);
        appendOrderFilters(jpql, fromCreatedAt, toCreatedBefore, customerId);
        if (variantIds != null && !variantIds.isEmpty()) {
            jpql.append("""
                      and exists (
                          select 1 from OrderItem filterItem
                          where filterItem.order = orders
                            and filterItem.isDeleted = false
                            and filterItem.productVariantId in :variantIds
                      )
                    """);
        }
        jpql.append("order by orders.createdAt asc");

        Query query = entityManager.createQuery(jpql.toString());
        bindOrderFilters(query, fromCreatedAt, toCreatedBefore, customerId, variantIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new OrderSummaryRow(
                        (UUID) row[0],
                        (Instant) row[1],
                        (BigDecimal) row[2]
                ))
                .toList();
    }

    public List<OrderItemTimelineRow> findOrderItemTimeline(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            UUID customerId,
            Collection<UUID> variantIds
    ) {
        StringBuilder jpql = new StringBuilder("""
                select
                    orders.id,
                    orders.createdAt,
                    item.productVariantId,
                    item.skuSnapshot,
                    item.productNameSnapshot,
                    item.variantNameSnapshot,
                    item.quantity,
                    item.lineTotal
                from OrderItem item
                join item.order orders
                where item.isDeleted = false
                  and orders.isDeleted = false
                  and orders.status in :saleStatuses
                  and orders.paymentStatus in :paidStatuses
                """);
        appendOrderFilters(jpql, fromCreatedAt, toCreatedBefore, customerId);
        if (variantIds != null && !variantIds.isEmpty()) {
            jpql.append("  and item.productVariantId in :variantIds\n");
        }
        jpql.append("order by orders.createdAt asc");

        Query query = entityManager.createQuery(jpql.toString());
        bindOrderFilters(query, fromCreatedAt, toCreatedBefore, customerId, variantIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new OrderItemTimelineRow(
                        (UUID) row[0],
                        (Instant) row[1],
                        (UUID) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        (BigDecimal) row[6],
                        (BigDecimal) row[7]
                ))
                .toList();
    }

    public List<CustomerOrderRow> findCustomerOrders(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            UUID customerId,
            Collection<UUID> variantIds
    ) {
        StringBuilder jpql = new StringBuilder("""
                select
                    orders.id,
                    orders.orderCode,
                    orders.customerId,
                    orders.customerNameSnapshot,
                    orders.customerPhoneSnapshot,
                    orders.createdAt,
                    orders.finalAmount,
                    orders.status,
                    orders.paymentStatus,
                    coalesce(sum(item.quantity), 0)
                from Order orders
                left join OrderItem item on item.order = orders and item.isDeleted = false
                where orders.isDeleted = false
                  and orders.status in :saleStatuses
                  and orders.paymentStatus in :paidStatuses
                """);
        appendOrderFilters(jpql, fromCreatedAt, toCreatedBefore, customerId);
        if (variantIds != null && !variantIds.isEmpty()) {
            jpql.append("  and item.productVariantId in :variantIds\n");
        }
        jpql.append("""
                group by orders.id, orders.orderCode, orders.customerId, orders.customerNameSnapshot,
                         orders.customerPhoneSnapshot, orders.createdAt, orders.finalAmount,
                         orders.status, orders.paymentStatus
                order by orders.createdAt desc
                """);

        Query query = entityManager.createQuery(jpql.toString());
        bindOrderFilters(query, fromCreatedAt, toCreatedBefore, customerId, variantIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new CustomerOrderRow(
                        (UUID) row[0],
                        (String) row[1],
                        (UUID) row[2],
                        (String) row[3],
                        (String) row[4],
                        (Instant) row[5],
                        (BigDecimal) row[6],
                        row[7] != null ? row[7].toString() : "",
                        row[8] != null ? row[8].toString() : "",
                        (BigDecimal) row[9]
                ))
                .toList();
    }

    public List<VariantSalesAggregateRow> findVariantSalesAggregates(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            Collection<UUID> variantIds
    ) {
        if (variantIds == null || variantIds.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select
                    item.productVariantId,
                    coalesce(sum(item.quantity), 0),
                    count(distinct orders.id)
                from OrderItem item
                join item.order orders
                where item.isDeleted = false
                  and orders.isDeleted = false
                  and orders.status in :saleStatuses
                  and orders.paymentStatus in :paidStatuses
                  and item.productVariantId in :variantIds
                """);
        appendOrderFilters(jpql, fromCreatedAt, toCreatedBefore, null);
        jpql.append("group by item.productVariantId");

        Query query = entityManager.createQuery(jpql.toString());
        bindOrderFilters(query, fromCreatedAt, toCreatedBefore, null, variantIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new VariantSalesAggregateRow(
                        (UUID) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    private void appendOrderFilters(
            StringBuilder jpql,
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            UUID customerId
    ) {
        if (fromCreatedAt != null) {
            jpql.append("  and orders.createdAt >= :fromCreatedAt\n");
        }
        if (toCreatedBefore != null) {
            jpql.append("  and orders.createdAt < :toCreatedBefore\n");
        }
        if (customerId != null) {
            jpql.append("  and orders.customerId = :customerId\n");
        }
    }

    private void bindOrderFilters(
            Query query,
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            UUID customerId,
            Collection<UUID> variantIds
    ) {
        query.setParameter("saleStatuses", SALE_ORDER_STATUSES);
        query.setParameter("paidStatuses", PAID_PAYMENT_STATUSES);
        if (fromCreatedAt != null) {
            query.setParameter("fromCreatedAt", fromCreatedAt);
        }
        if (toCreatedBefore != null) {
            query.setParameter("toCreatedBefore", toCreatedBefore);
        }
        if (customerId != null) {
            query.setParameter("customerId", customerId);
        }
        if (variantIds != null && !variantIds.isEmpty()) {
            query.setParameter("variantIds", variantIds);
        }
    }

    public record OrderSummaryRow(
            UUID orderId,
            Instant createdAt,
            BigDecimal finalAmount
    ) {
    }

    public record OrderItemTimelineRow(
            UUID orderId,
            Instant createdAt,
            UUID productVariantId,
            String sku,
            String productName,
            String variantName,
            BigDecimal quantity,
            BigDecimal lineTotal
    ) {
    }

    public record CustomerOrderRow(
            UUID orderId,
            String orderCode,
            UUID customerId,
            String customerName,
            String customerPhone,
            Instant createdAt,
            BigDecimal finalAmount,
            String status,
            String paymentStatus,
            BigDecimal itemQuantity
    ) {
    }

    public record VariantSalesAggregateRow(
            UUID productVariantId,
            BigDecimal quantitySold,
            long orderCount
    ) {
    }
}
