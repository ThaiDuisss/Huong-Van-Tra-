package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderQueryCriteriaBuilder orderQueryCriteriaBuilder;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private OrderQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryServiceImpl(
                orderRepository,
                orderMapper,
                orderQueryCriteriaBuilder,
                orderResponseAssembler
        );
    }

    @Test
    void getOrderByIdKeepsRepositoryThenAssemblerCallOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.getOrderById(orderId);

        assertSame(expected, actual);
        InOrder inOrder = inOrder(orderRepository, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoMoreInteractions(orderRepository, orderResponseAssembler);
        verifyNoInteractions(orderMapper, orderQueryCriteriaBuilder);
    }

    @Test
    void getPaymentInfoKeepsRepositoryThenMapperCallOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        OrderPaymentInfoResponse expected = OrderPaymentInfoResponse.builder()
                .orderId(orderId)
                .build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toPaymentInfoResponse(order)).thenReturn(expected);

        OrderPaymentInfoResponse actual = service.getPaymentInfo(orderId);

        assertSame(expected, actual);
        InOrder inOrder = inOrder(orderRepository, orderMapper);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderMapper).toPaymentInfoResponse(order);
        verifyNoMoreInteractions(orderRepository, orderMapper);
        verifyNoInteractions(orderResponseAssembler, orderQueryCriteriaBuilder);
    }

    @Test
    void getOrderByIdKeepsExistingNotFoundMessageAndStopsMapping() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.getOrderById(orderId)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(orderMapper, orderQueryCriteriaBuilder, orderResponseAssembler);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOrdersPreservesFiltersBusinessSortPagingClampAndMapping() {
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 24);
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        OrderSummaryResponse summary = OrderSummaryResponse.builder().id(orderId).build();
        Specification<Order> filters = mock(Specification.class);
        Specification<Order> sorted = mock(Specification.class);
        PageRequest expectedPageRequest = PageRequest.of(0, 200);
        when(orderQueryCriteriaBuilder.build(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate
        )).thenReturn(filters);
        when(orderQueryCriteriaBuilder.normalizeSort("invoicePayment")).thenReturn("invoicePayment");
        when(orderQueryCriteriaBuilder.withBusinessOrder(filters, "invoicePayment")).thenReturn(sorted);
        when(orderRepository.findAll(sorted, expectedPageRequest))
                .thenReturn(new PageImpl<>(List.of(order), expectedPageRequest, 1));
        when(orderMapper.toSummaryResponse(order)).thenReturn(summary);

        Page<OrderSummaryResponse> result = service.getOrders(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate,
                "invoicePayment",
                -10,
                999
        );

        assertEquals(List.of(summary), result.getContent());
        assertEquals(0, result.getNumber());
        assertEquals(200, result.getSize());
        InOrder inOrder = inOrder(orderQueryCriteriaBuilder, orderRepository, orderMapper);
        inOrder.verify(orderQueryCriteriaBuilder).build(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate
        );
        inOrder.verify(orderQueryCriteriaBuilder).normalizeSort("invoicePayment");
        inOrder.verify(orderQueryCriteriaBuilder).withBusinessOrder(filters, "invoicePayment");
        inOrder.verify(orderRepository).findAll(sorted, expectedPageRequest);
        inOrder.verify(orderMapper).toSummaryResponse(order);
        verifyNoInteractions(orderResponseAssembler);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOrderStatsPreservesCountOrderAndBuckets() {
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 24);
        Specification<Order> total = mock(Specification.class);
        Specification<Order> pending = mock(Specification.class);
        Specification<Order> processing = mock(Specification.class);
        Specification<Order> completed = mock(Specification.class);
        Specification<Order> refunded = mock(Specification.class);
        Specification<Order> unpaid = mock(Specification.class);
        Specification<Order> paid = mock(Specification.class);
        when(orderQueryCriteriaBuilder.build(null, null, null, null, fromDate, toDate))
                .thenReturn(total);
        when(orderQueryCriteriaBuilder.byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT)
        )).thenReturn(pending);
        when(orderQueryCriteriaBuilder.byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.PAYMENT_CONFIRMED)
        )).thenReturn(processing);
        when(orderQueryCriteriaBuilder.byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.COMPLETED)
        )).thenReturn(completed);
        when(orderQueryCriteriaBuilder.byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED)
        )).thenReturn(refunded);
        when(orderQueryCriteriaBuilder.byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT)
        )).thenReturn(unpaid);
        when(orderQueryCriteriaBuilder.byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.PAID)
        )).thenReturn(paid);
        when(orderRepository.count(total)).thenReturn(10L);
        when(orderRepository.count(pending)).thenReturn(2L);
        when(orderRepository.count(processing)).thenReturn(3L);
        when(orderRepository.count(completed)).thenReturn(4L);
        when(orderRepository.count(refunded)).thenReturn(5L);
        when(orderRepository.count(unpaid)).thenReturn(6L);
        when(orderRepository.count(paid)).thenReturn(7L);

        OrderStatsResponse result = service.getOrderStats(fromDate, toDate);

        assertEquals(10L, result.getTotal());
        assertEquals(2L, result.getPending());
        assertEquals(3L, result.getProcessing());
        assertEquals(0, result.getShipping());
        assertEquals(4L, result.getCompleted());
        assertEquals(5L, result.getReturnedOrRefunded());
        assertEquals(6L, result.getUnpaidInvoices());
        assertEquals(7L, result.getPaidInvoices());
        InOrder inOrder = inOrder(orderQueryCriteriaBuilder, orderRepository);
        inOrder.verify(orderQueryCriteriaBuilder).build(null, null, null, null, fromDate, toDate);
        inOrder.verify(orderRepository).count(total);
        inOrder.verify(orderQueryCriteriaBuilder).byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT)
        );
        inOrder.verify(orderRepository).count(pending);
        inOrder.verify(orderQueryCriteriaBuilder).byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.PAYMENT_CONFIRMED)
        );
        inOrder.verify(orderRepository).count(processing);
        inOrder.verify(orderQueryCriteriaBuilder).byStatuses(
                fromDate,
                toDate,
                List.of(OrderStatus.COMPLETED)
        );
        inOrder.verify(orderRepository).count(completed);
        inOrder.verify(orderQueryCriteriaBuilder).byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED)
        );
        inOrder.verify(orderRepository).count(refunded);
        inOrder.verify(orderQueryCriteriaBuilder).byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT)
        );
        inOrder.verify(orderRepository).count(unpaid);
        inOrder.verify(orderQueryCriteriaBuilder).byPaymentStatuses(
                fromDate,
                toDate,
                List.of(PaymentStatus.PAID)
        );
        inOrder.verify(orderRepository).count(paid);
        verifyNoInteractions(orderMapper, orderResponseAssembler);
    }

    @Test
    void queryMethodsKeepReadOnlyTransactionsOnImplementation() throws NoSuchMethodException {
        assertReadOnly("getOrderById", UUID.class);
        assertReadOnly("getPaymentInfo", UUID.class);
        assertReadOnly(
                "getOrders",
                String.class,
                OrderStatus.class,
                PaymentStatus.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class,
                int.class,
                int.class
        );
        assertReadOnly("getOrderStats", LocalDate.class, LocalDate.class);
    }

    private void assertReadOnly(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = OrderQueryServiceImpl.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    private Order order(UUID orderId) {
        Order order = Order.builder().build();
        order.setId(orderId);
        return order;
    }
}
