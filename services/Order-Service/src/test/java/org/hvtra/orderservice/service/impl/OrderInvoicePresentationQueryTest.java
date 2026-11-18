package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderReturnEligibility;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderInvoicePresentationQueryTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderServiceImpl service = new OrderServiceImpl(repository, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    @Test
    @SuppressWarnings("unchecked")
    void activeInvoiceEndpointIncludesRefundedPaymentsWithoutChangingOrderStatus() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getOrders(null, null, PaymentStatus.PAID, null, null, null, null, "invoicePayment", 0, 10);
        ArgumentCaptor<Specification<Order>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        Root<Order> root = mock(Root.class);
        Path<PaymentStatus> payment = mock(Path.class);
        when(root.<PaymentStatus>get("paymentStatus")).thenReturn(payment);
        CriteriaQuery<Long> query = mock(CriteriaQuery.class);
        when(query.getResultType()).thenReturn(Long.class);
        captor.getValue().toPredicate(root, query, mock(CriteriaBuilder.class));
        verify(payment).in(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
        verify(root, never()).get("status");
        verify(repository, never()).save(any(Order.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryPaidQueriesAndReturnEligibilityDoNotChangeTheirFilterMeaning() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getOrders(null, null, PaymentStatus.PAID, null, null, null, null, "businessStatus", 0, 10);
        service.getOrders(null, null, PaymentStatus.PAID, null, null, null, OrderReturnEligibility.ALL, "invoicePayment", 0, 10);
        ArgumentCaptor<Specification<Order>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository, times(2)).findAll(captor.capture(), any(Pageable.class));
        for (Specification<Order> spec : captor.getAllValues()) {
            Root<Order> root = mock(Root.class);
            Path<PaymentStatus> payment = mock(Path.class);
            when(root.<PaymentStatus>get("paymentStatus")).thenReturn(payment);
            CriteriaQuery<Long> query = mock(CriteriaQuery.class);
            when(query.getResultType()).thenReturn(Long.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            spec.toPredicate(root, query, builder);
            verify(builder).equal(payment, PaymentStatus.PAID);
            verify(payment, never()).in(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingCountMatchesTheActualPendingLifecycleFilter() {
        service.getOrderStats(null, null);
        ArgumentCaptor<Specification<Order>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository, times(7)).count(captor.capture());
        Root<Order> root = mock(Root.class);
        Path<OrderStatus> status = mock(Path.class);
        when(root.<OrderStatus>get("status")).thenReturn(status);
        CriteriaQuery<Long> query = mock(CriteriaQuery.class);
        when(query.getResultType()).thenReturn(Long.class);
        captor.getAllValues().get(1).toPredicate(root, query, mock(CriteriaBuilder.class));
        verify(status).in(List.of(OrderStatus.PENDING_PAYMENT));
    }
}
