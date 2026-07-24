package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodaySalesServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderQueryCriteriaBuilder orderQueryCriteriaBuilder;

    @Mock
    private OrderDateTimeHelper orderDateTimeHelper;

    @Mock
    private OrderAmountCalculator orderAmountCalculator;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private TodaySalesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TodaySalesServiceImpl(
                orderRepository,
                orderQueryCriteriaBuilder,
                orderDateTimeHelper,
                orderAmountCalculator,
                orderResponseAssembler
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTodaySalesKeepsTodayCriteriaSortEnrichmentFilterAndPaging() {
        LocalDate today = LocalDate.of(2026, 7, 24);
        Order first = order();
        Order second = order();
        Order third = order();
        TodaySalesItemResponse firstItem = item("BANK_TRANSFER");
        TodaySalesItemResponse secondItem = item("CASH");
        TodaySalesItemResponse thirdItem = item("BANK_TRANSFER");
        Specification<Order> todaySpec = mock(Specification.class);
        Sort expectedSort = Sort.by(Sort.Direction.DESC, "createdAt");
        when(orderDateTimeHelper.today()).thenReturn(today);
        when(orderQueryCriteriaBuilder.build(
                null,
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                null,
                today,
                today
        )).thenReturn(todaySpec);
        when(orderRepository.findAll(todaySpec, expectedSort)).thenReturn(List.of(first, second, third));
        when(orderResponseAssembler.toTodaySalesItemResponse(first)).thenReturn(firstItem);
        when(orderResponseAssembler.paymentMethodMatches(firstItem, "bank")).thenReturn(true);
        when(orderResponseAssembler.toTodaySalesItemResponse(second)).thenReturn(secondItem);
        when(orderResponseAssembler.paymentMethodMatches(secondItem, "bank")).thenReturn(false);
        when(orderResponseAssembler.toTodaySalesItemResponse(third)).thenReturn(thirdItem);
        when(orderResponseAssembler.paymentMethodMatches(thirdItem, "bank")).thenReturn(true);

        Page<TodaySalesItemResponse> result = service.getTodaySales(
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "bank",
                -5,
                1
        );

        assertEquals(List.of(firstItem), result.getContent());
        assertEquals(0, result.getNumber());
        assertEquals(1, result.getSize());
        assertEquals(2, result.getTotalElements());
        InOrder inOrder = inOrder(orderDateTimeHelper, orderQueryCriteriaBuilder, orderRepository, orderResponseAssembler);
        inOrder.verify(orderDateTimeHelper).today();
        inOrder.verify(orderQueryCriteriaBuilder).build(
                null,
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                null,
                today,
                today
        );
        inOrder.verify(orderRepository).findAll(todaySpec, expectedSort);
        inOrder.verify(orderResponseAssembler).toTodaySalesItemResponse(first);
        inOrder.verify(orderResponseAssembler).paymentMethodMatches(firstItem, "bank");
        inOrder.verify(orderResponseAssembler).toTodaySalesItemResponse(second);
        inOrder.verify(orderResponseAssembler).paymentMethodMatches(secondItem, "bank");
        inOrder.verify(orderResponseAssembler).toTodaySalesItemResponse(third);
        inOrder.verify(orderResponseAssembler).paymentMethodMatches(thirdItem, "bank");
        verifyNoInteractions(orderAmountCalculator);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTodaySalesSummaryKeepsTodayCriteriaRepositoryAndCalculatorOrder() {
        LocalDate today = LocalDate.of(2026, 7, 24);
        Order first = order();
        Order second = order();
        List<Order> todayOrders = List.of(first, second);
        Specification<Order> todaySpec = mock(Specification.class);
        TodaySalesSummaryResponse expected = TodaySalesSummaryResponse.builder()
                .todayRevenue(new BigDecimal("250000.00"))
                .totalOrders(2)
                .completedOrders(1)
                .pendingPaymentOrders(1)
                .averageOrderValue(new BigDecimal("125000.00"))
                .pendingAmount(new BigDecimal("50000.00"))
                .build();
        when(orderDateTimeHelper.today()).thenReturn(today);
        when(orderQueryCriteriaBuilder.build(null, null, null, null, today, today))
                .thenReturn(todaySpec);
        when(orderRepository.findAll(todaySpec)).thenReturn(todayOrders);
        when(orderAmountCalculator.calculateTodaySalesSummary(todayOrders)).thenReturn(expected);

        TodaySalesSummaryResponse result = service.getTodaySalesSummary();

        assertSame(expected, result);
        InOrder inOrder = inOrder(orderDateTimeHelper, orderQueryCriteriaBuilder, orderRepository, orderAmountCalculator);
        inOrder.verify(orderDateTimeHelper).today();
        inOrder.verify(orderQueryCriteriaBuilder).build(null, null, null, null, today, today);
        inOrder.verify(orderRepository).findAll(todaySpec);
        inOrder.verify(orderAmountCalculator).calculateTodaySalesSummary(todayOrders);
        verifyNoInteractions(orderResponseAssembler);
    }

    @Test
    void todaySalesMethodsKeepReadOnlyTransactionsOnImplementation() throws NoSuchMethodException {
        assertReadOnly(
                "getTodaySales",
                OrderStatus.class,
                PaymentStatus.class,
                String.class,
                int.class,
                int.class
        );
        assertReadOnly("getTodaySalesSummary");
    }

    private void assertReadOnly(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = TodaySalesServiceImpl.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }

    private Order order() {
        Order order = Order.builder().build();
        order.setId(UUID.randomUUID());
        return order;
    }

    private TodaySalesItemResponse item(String paymentMethod) {
        return TodaySalesItemResponse.builder()
                .orderId(UUID.randomUUID())
                .paymentMethod(paymentMethod)
                .build();
    }
}
