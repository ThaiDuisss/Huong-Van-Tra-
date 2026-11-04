package org.hvtra.orderservice.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Binding and aggregate mapping only; the existing metamodel suite separately checks JPQL syntax. */
@ExtendWith(MockitoExtension.class)
class OrderStatisticQueryBindingTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FROM = Instant.parse("2026-11-02T17:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-03T17:00:00Z");
    @Mock EntityManager em;
    @Mock Query query;

    static Stream<Arguments> cases() {
        return Stream.of("staff", "staff-items", "top", "totals", "returns", "methods", "branches", "timeline")
                .flatMap(name -> Stream.of(Arguments.of(name, false, false), Arguments.of(name, true, false),
                        Arguments.of(name, false, true), Arguments.of(name, true, true)));
    }

    @ParameterizedTest @MethodSource("cases")
    void aggregateQueriesBindOnlyPresentDateBoundsAndPreserveFinancialValues(String operation, boolean hasFrom, boolean hasTo) {
        var repository = new OrderStatisticReportQueryRepository(em);
        when(em.createQuery(anyString())).thenReturn(query);
        Object[] row = switch (operation) {
            case "staff" -> new Object[]{ID, "Cashier", 2, new BigDecimal("123.4500"), 1L};
            case "staff-items", "returns" -> new Object[]{ID, new BigDecimal("1.250")};
            case "top" -> new Object[]{ID, "SKU", "Tea frozen", "100g", new BigDecimal("1.250"), new BigDecimal("123.4500"), 2L};
            case "totals" -> new Object[]{1L, new BigDecimal("1.250"), new BigDecimal("123.4500"), 2};
            case "methods" -> new Object[]{"CASH", 2L, new BigDecimal("123.4500")};
            case "branches" -> new Object[]{ID, "Branch", 2L, new BigDecimal("123.4500")};
            case "timeline" -> new Object[]{FROM, new BigDecimal("123.4500")};
            default -> throw new AssertionError(operation);
        };
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));
        Instant from = hasFrom ? FROM : null;
        Instant to = hasTo ? TO : null;
        switch (operation) {
            case "staff" -> {
                assertEquals(List.of(new OrderStatisticReportQueryRepository.StaffOrderMetrics(ID, "Cashier", 2, new BigDecimal("123.4500"), 1)),
                        repository.findStaffOrderMetrics(from, to));
                verify(query).setParameter("refundedStatuses", List.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
            }
            case "staff-items" -> assertEquals(Map.of(ID, new BigDecimal("1.250")), repository.findStaffItemsSold(from, to));
            case "top" -> {
                assertEquals(List.of(new OrderStatisticReportQueryRepository.ProductSalesMetrics(ID, "SKU", "Tea frozen", "100g",
                        new BigDecimal("1.250"), new BigDecimal("123.4500"), 2)), repository.findTopSellingProducts(from, to, hasFrom, 5));
                verify(query).setMaxResults(5);
            }
            case "totals" -> assertEquals(new OrderStatisticReportQueryRepository.ProductSalesTotals(1, new BigDecimal("1.250"),
                    new BigDecimal("123.4500"), 2), repository.findProductSalesTotals(from, to));
            case "returns" -> {
                assertEquals(Map.of(ID, new BigDecimal("1.250")), repository.findReturnedQuantities(from, to, List.of(ID)));
                verify(query).setParameter("completedReturnStatus", ReturnStatus.COMPLETED);
                verify(query).setParameter("variantIds", List.of(ID));
            }
            case "methods" -> assertEquals(List.of(new OrderStatisticReportQueryRepository.PaymentMethodMetrics("CASH", 2,
                    new BigDecimal("123.4500"))), repository.findPaymentBreakdown(from, to));
            case "branches" -> assertEquals(List.of(new OrderStatisticReportQueryRepository.BranchSalesMetrics(ID, "Branch", 2,
                    new BigDecimal("123.4500"))), repository.findStorePerformance(from, to));
            case "timeline" -> assertEquals(List.of(new OrderStatisticReportQueryRepository.OrderTimelineEntry(FROM,
                    new BigDecimal("123.4500"))), repository.findPaidOrderTimeline(from, to));
        }
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(jpql.capture());
        assertTrue(jpql.getValue().contains("orders.isDeleted = false"));
        assertEquals(hasFrom, jpql.getValue().contains("orders.createdAt >= :fromCreatedAt"));
        assertEquals(hasTo, jpql.getValue().contains("orders.createdAt < :toCreatedBefore"));
        verify(query).setParameter("saleStatuses", List.of(OrderStatus.COMPLETED, OrderStatus.PAYMENT_CONFIRMED));
        verify(query).setParameter("paidStatuses", List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        if (hasFrom) verify(query).setParameter("fromCreatedAt", FROM);
        else verify(query, never()).setParameter(eq("fromCreatedAt"), any());
        if (hasTo) verify(query).setParameter("toCreatedBefore", TO);
        else verify(query, never()).setParameter(eq("toCreatedBefore"), any());
        if (operation.equals("top")) assertTrue(jpql.getValue().contains(hasFrom ? "order by sum(item.lineTotal) desc" : "order by sum(item.quantity) desc"));
        if (operation.equals("top") || operation.equals("totals")) assertTrue(jpql.getValue().contains("count(distinct orders.id)"));
        verifyNoMoreInteractions(em);
    }

    @Test void emptyAggregatesReturnZerosAndNoVariantIdsSkipQueryEntirely() {
        var repository = new OrderStatisticReportQueryRepository(em);
        assertTrue(repository.findReturnedQuantities(null, null, null).isEmpty());
        assertTrue(repository.findReturnedQuantities(null, null, List.of()).isEmpty());
        verifyNoInteractions(em, query);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        assertEquals(new OrderStatisticReportQueryRepository.ProductSalesTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                repository.findProductSalesTotals(null, null));
    }

    @Test void nullableAndAlternateNumericAggregateTypesDoNotLosePrecisionOrInventCounts() {
        var repository = new OrderStatisticReportQueryRepository(em);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{null, null, null, "12.345", null}));
        assertEquals(List.of(new OrderStatisticReportQueryRepository.StaffOrderMetrics(null, null, 0, new BigDecimal("12.345"), 0)),
                repository.findStaffOrderMetrics(null, null));
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{null, null, null, null}));
        assertEquals(new OrderStatisticReportQueryRepository.ProductSalesTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                repository.findProductSalesTotals(null, null));
    }
}
