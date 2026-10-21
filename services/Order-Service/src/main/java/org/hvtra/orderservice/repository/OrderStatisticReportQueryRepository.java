package org.hvtra.orderservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Truy vấn tổng hợp cho báo cáo thống kê bán hàng. Mọi phép cộng/đếm đều chạy ở DB (group by),
 * không tải danh sách đơn hàng vào bộ nhớ.
 *
 * <p>"Đơn đã bán" = chưa xóa, trạng thái {@link #SALE_ORDER_STATUSES} và thanh toán
 * {@link #PAID_PAYMENT_STATUSES}. Đơn đã hoàn tiền vẫn là đơn đã bán (số gộp); phần hoàn trả
 * được trả về ở cột riêng.
 */
@Repository
public class OrderStatisticReportQueryRepository {

    public static final List<OrderStatus> SALE_ORDER_STATUSES =
            List.of(OrderStatus.COMPLETED, OrderStatus.PAYMENT_CONFIRMED);
    public static final List<PaymentStatus> PAID_PAYMENT_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    public static final List<PaymentStatus> REFUNDED_PAYMENT_STATUSES =
            List.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    static final String PAID_ORDER_FILTER = """
            orders.isDeleted = false
              and orders.status in :saleStatuses
              and orders.paymentStatus in :paidStatuses
            """;
    static final String FROM_FILTER = "  and orders.createdAt >= :fromCreatedAt\n";
    static final String TO_FILTER = "  and orders.createdAt < :toCreatedBefore\n";

    static final String STAFF_ORDER_METRICS_SELECT = """
            select
                orders.cashierId,
                max(orders.cashierNameSnapshot),
                count(orders.id),
                coalesce(sum(orders.finalAmount), 0),
                coalesce(sum(case when orders.paymentStatus in :refundedStatuses then 1 else 0 end), 0)
            from Order orders
            where
            """;
    static final String STAFF_ITEMS_SOLD_SELECT = """
            select
                orders.cashierId,
                coalesce(sum(item.quantity), 0)
            from OrderItem item
            join item.order orders
            where item.isDeleted = false
              and
            """;
    static final String PRODUCT_SELECT = """
            select
                item.productVariantId,
                max(item.skuSnapshot),
                max(item.productNameSnapshot),
                max(item.variantNameSnapshot),
                coalesce(sum(item.quantity), 0),
                coalesce(sum(item.lineTotal), 0),
                count(distinct orders.id)
            from OrderItem item
            join item.order orders
            where item.isDeleted = false
              and
            """;
    static final String PRODUCT_TOTALS_SELECT = """
            select
                count(distinct item.productVariantId),
                coalesce(sum(item.quantity), 0),
                coalesce(sum(item.lineTotal), 0),
                count(distinct orders.id)
            from OrderItem item
            join item.order orders
            where item.isDeleted = false
              and
            """;
    static final String RETURNED_QUANTITY_SELECT = """
            select
                item.productVariantId,
                coalesce(sum(returnItem.quantity), 0)
            from OrderReturnItem returnItem
            join returnItem.orderReturn orderReturn
            join returnItem.orderItem item
            join item.order orders
            where returnItem.isDeleted = false
              and orderReturn.isDeleted = false
              and orderReturn.status = :completedReturnStatus
              and item.isDeleted = false
              and item.productVariantId in :variantIds
              and
            """;
    static final String GROUP_BY_CASHIER = "group by orders.cashierId\n";
    static final String GROUP_BY_VARIANT = "group by item.productVariantId\n";
    static final String ORDER_BY_QUANTITY =
            "order by sum(item.quantity) desc, sum(item.lineTotal) desc, item.productVariantId asc\n";
    static final String ORDER_BY_REVENUE =
            "order by sum(item.lineTotal) desc, sum(item.quantity) desc, item.productVariantId asc\n";

    @PersistenceContext
    private EntityManager entityManager;

    public OrderStatisticReportQueryRepository() {
    }

    public OrderStatisticReportQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Một dòng cho mỗi thu ngân (kể cả nhóm cashierId = null). */
    public List<StaffOrderMetrics> findStaffOrderMetrics(Instant fromCreatedAt, Instant toCreatedBefore) {
        Query query = entityManager.createQuery(
                staffOrderMetricsJpql(fromCreatedAt != null, toCreatedBefore != null)
        );
        bindPaidOrderFilter(query, fromCreatedAt, toCreatedBefore);
        query.setParameter("refundedStatuses", REFUNDED_PAYMENT_STATUSES);

        return rows(query).stream()
                .map(row -> new StaffOrderMetrics(
                        (UUID) row[0],
                        (String) row[1],
                        toLong(row[2]),
                        toBigDecimal(row[3]),
                        toLong(row[4])
                ))
                .toList();
    }

    /** Tổng số lượng hàng bán theo thu ngân; key null là nhóm đơn không có thu ngân. */
    public Map<UUID, BigDecimal> findStaffItemsSold(Instant fromCreatedAt, Instant toCreatedBefore) {
        Query query = entityManager.createQuery(
                staffItemsSoldJpql(fromCreatedAt != null, toCreatedBefore != null)
        );
        bindPaidOrderFilter(query, fromCreatedAt, toCreatedBefore);

        Map<UUID, BigDecimal> result = new HashMap<>();
        rows(query).forEach(row -> result.put((UUID) row[0], toBigDecimal(row[1])));
        return result;
    }

    public List<ProductSalesMetrics> findTopSellingProducts(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            boolean sortByRevenue,
            int limit
    ) {
        Query query = entityManager.createQuery(
                topSellingProductsJpql(fromCreatedAt != null, toCreatedBefore != null, sortByRevenue)
        );
        bindPaidOrderFilter(query, fromCreatedAt, toCreatedBefore);
        query.setMaxResults(limit);

        return rows(query).stream()
                .map(row -> new ProductSalesMetrics(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        toBigDecimal(row[4]),
                        toBigDecimal(row[5]),
                        toLong(row[6])
                ))
                .toList();
    }

    public ProductSalesTotals findProductSalesTotals(Instant fromCreatedAt, Instant toCreatedBefore) {
        Query query = entityManager.createQuery(
                productSalesTotalsJpql(fromCreatedAt != null, toCreatedBefore != null)
        );
        bindPaidOrderFilter(query, fromCreatedAt, toCreatedBefore);

        List<Object[]> rows = rows(query);
        if (rows.isEmpty()) {
            return new ProductSalesTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        Object[] row = rows.get(0);
        return new ProductSalesTotals(toLong(row[0]), toBigDecimal(row[1]), toBigDecimal(row[2]), toLong(row[3]));
    }

    /** Số lượng đã trả (phiếu hoàn COMPLETED = đã hoàn tiền) của các đơn bán trong kỳ, theo biến thể. */
    public Map<UUID, BigDecimal> findReturnedQuantities(
            Instant fromCreatedAt,
            Instant toCreatedBefore,
            Collection<UUID> variantIds
    ) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        Query query = entityManager.createQuery(
                returnedQuantitiesJpql(fromCreatedAt != null, toCreatedBefore != null)
        );
        bindPaidOrderFilter(query, fromCreatedAt, toCreatedBefore);
        query.setParameter("completedReturnStatus", ReturnStatus.COMPLETED);
        query.setParameter("variantIds", variantIds);

        Map<UUID, BigDecimal> result = new HashMap<>();
        rows(query).forEach(row -> result.put((UUID) row[0], toBigDecimal(row[1])));
        return result;
    }

    static String staffOrderMetricsJpql(boolean hasFrom, boolean hasTo) {
        return STAFF_ORDER_METRICS_SELECT + paidOrderFilter(hasFrom, hasTo) + GROUP_BY_CASHIER;
    }

    static String staffItemsSoldJpql(boolean hasFrom, boolean hasTo) {
        return STAFF_ITEMS_SOLD_SELECT + paidOrderFilter(hasFrom, hasTo) + GROUP_BY_CASHIER;
    }

    static String topSellingProductsJpql(boolean hasFrom, boolean hasTo, boolean sortByRevenue) {
        return PRODUCT_SELECT
                + paidOrderFilter(hasFrom, hasTo)
                + GROUP_BY_VARIANT
                + (sortByRevenue ? ORDER_BY_REVENUE : ORDER_BY_QUANTITY);
    }

    static String productSalesTotalsJpql(boolean hasFrom, boolean hasTo) {
        return PRODUCT_TOTALS_SELECT + paidOrderFilter(hasFrom, hasTo);
    }

    static String returnedQuantitiesJpql(boolean hasFrom, boolean hasTo) {
        return RETURNED_QUANTITY_SELECT + paidOrderFilter(hasFrom, hasTo) + GROUP_BY_VARIANT;
    }

    private static String paidOrderFilter(boolean hasFrom, boolean hasTo) {
        return PAID_ORDER_FILTER + (hasFrom ? FROM_FILTER : "") + (hasTo ? TO_FILTER : "");
    }

    private void bindPaidOrderFilter(Query query, Instant fromCreatedAt, Instant toCreatedBefore) {
        query.setParameter("saleStatuses", SALE_ORDER_STATUSES);
        query.setParameter("paidStatuses", PAID_PAYMENT_STATUSES);
        if (fromCreatedAt != null) {
            query.setParameter("fromCreatedAt", fromCreatedAt);
        }
        if (toCreatedBefore != null) {
            query.setParameter("toCreatedBefore", toCreatedBefore);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query query) {
        return (List<Object[]>) query.getResultList();
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    public record StaffOrderMetrics(
            UUID cashierId,
            String cashierName,
            long orderCount,
            BigDecimal revenue,
            long refundedOrderCount
    ) {
    }

    public record ProductSalesMetrics(
            UUID productVariantId,
            String sku,
            String productName,
            String variantName,
            BigDecimal quantitySold,
            BigDecimal revenue,
            long orderCount
    ) {
    }

    public record ProductSalesTotals(
            long productCount,
            BigDecimal quantitySold,
            BigDecimal revenue,
            long orderCount
    ) {
    }
}
