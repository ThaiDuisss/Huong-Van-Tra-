package org.hvtra.orderservice.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra cú pháp/ngữ nghĩa JPQL của báo cáo thống kê trên metamodel thật của các entity,
 * không cần kết nối DB (Hibernate chỉ dựng SessionFactory với dialect MySQL, không truy cập JDBC).
 */
class OrderStatisticReportQueryRepositoryTest {

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void buildOfflineSessionFactory() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Order.class)
                .addAnnotatedClass(OrderItem.class)
                .addAnnotatedClass(OrderDiscount.class)
                .addAnnotatedClass(OrderReturn.class)
                .addAnnotatedClass(OrderReturnItem.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void everyReportQueryVariantIsValidJpql() {
        try (EntityManager entityManager = sessionFactory.createEntityManager()) {
            for (boolean hasFrom : List.of(true, false)) {
                for (boolean hasTo : List.of(true, false)) {
                    for (String jpql : List.of(
                            OrderStatisticReportQueryRepository.staffOrderMetricsJpql(hasFrom, hasTo),
                            OrderStatisticReportQueryRepository.staffItemsSoldJpql(hasFrom, hasTo),
                            OrderStatisticReportQueryRepository.topSellingProductsJpql(hasFrom, hasTo, true),
                            OrderStatisticReportQueryRepository.topSellingProductsJpql(hasFrom, hasTo, false),
                            OrderStatisticReportQueryRepository.productSalesTotalsJpql(hasFrom, hasTo),
                            OrderStatisticReportQueryRepository.returnedQuantitiesJpql(hasFrom, hasTo)
                    )) {
                        assertDoesNotThrow(() -> entityManager.createQuery(jpql), jpql);
                    }
                }
            }
        }
    }

    @Test
    void offlineValidationActuallyRejectsUnknownAttributes() {
        try (EntityManager entityManager = sessionFactory.createEntityManager()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> entityManager.createQuery("select orders.noSuchField from Order orders")
            );
        }
    }

    @Test
    void dateFiltersAreOnlyAppendedWhenProvided() {
        String unbounded = OrderStatisticReportQueryRepository.topSellingProductsJpql(false, false, false);
        String bounded = OrderStatisticReportQueryRepository.topSellingProductsJpql(true, true, false);

        assertFalse(unbounded.contains(":fromCreatedAt"));
        assertFalse(unbounded.contains(":toCreatedBefore"));
        assertTrue(bounded.contains("orders.createdAt >= :fromCreatedAt"));
        assertTrue(bounded.contains("orders.createdAt < :toCreatedBefore"));
    }

    @Test
    void productSortSwitchesBetweenQuantityAndRevenue() {
        assertTrue(OrderStatisticReportQueryRepository.topSellingProductsJpql(false, false, false)
                .contains("order by sum(item.quantity) desc"));
        assertTrue(OrderStatisticReportQueryRepository.topSellingProductsJpql(false, false, true)
                .contains("order by sum(item.lineTotal) desc"));
    }

    @Test
    void paidOrderDefinitionExcludesUnpaidAndCancelledOrders() {
        assertEquals(
                List.of(OrderStatus.COMPLETED, OrderStatus.PAYMENT_CONFIRMED),
                OrderStatisticReportQueryRepository.SALE_ORDER_STATUSES
        );
        assertEquals(
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
                OrderStatisticReportQueryRepository.PAID_PAYMENT_STATUSES
        );
        for (String jpql : List.of(
                OrderStatisticReportQueryRepository.staffOrderMetricsJpql(true, true),
                OrderStatisticReportQueryRepository.staffItemsSoldJpql(true, true),
                OrderStatisticReportQueryRepository.topSellingProductsJpql(true, true, false),
                OrderStatisticReportQueryRepository.productSalesTotalsJpql(true, true),
                OrderStatisticReportQueryRepository.returnedQuantitiesJpql(true, true)
        )) {
            assertTrue(jpql.contains("orders.isDeleted = false"), jpql);
            assertTrue(jpql.contains("orders.status in :saleStatuses"), jpql);
            assertTrue(jpql.contains("orders.paymentStatus in :paidStatuses"), jpql);
        }
    }
}
