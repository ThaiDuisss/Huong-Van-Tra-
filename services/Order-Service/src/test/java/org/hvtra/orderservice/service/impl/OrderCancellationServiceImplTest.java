package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private OrderCancellationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationServiceImpl(
                orderRepository,
                orderValidator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void cancelOrderKeepsValidateSaveHistoryAndResponseOrder() {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Khách hủy");
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.cancelOrder(orderId, request);

        assertSame(expected, actual);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        InOrder inOrder = inOrder(orderRepository, orderValidator, orderStatusHistoryWriter, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateCancellation(order);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, "Khách hủy", null);
        inOrder.verify(orderResponseAssembler).toResponse(order);
    }

    @Test
    void cancelOrderKeepsExistingNotFoundMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Khách hủy");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.cancelOrder(orderId, request)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(orderValidator, orderStatusHistoryWriter, orderResponseAssembler);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void cancelOrderPropagatesInvalidStatusBeforeSavingHistoryOrResponse() {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Khách hủy");
        Order order = order(orderId, OrderStatus.COMPLETED, PaymentStatus.PAID);
        InvalidOrderStatusException expected = new InvalidOrderStatusException("Không thể hủy");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        doThrow(expected).when(orderValidator).validateCancellation(order);

        InvalidOrderStatusException actual = assertThrows(
                InvalidOrderStatusException.class,
                () -> service.cancelOrder(orderId, request)
        );

        assertSame(expected, actual);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        verifyNoInteractions(orderStatusHistoryWriter, orderResponseAssembler);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void cancelOrderKeepsTransactionOnImplementation() throws NoSuchMethodException {
        Method method = OrderCancellationServiceImpl.class.getMethod(
                "cancelOrder",
                UUID.class,
                CancelOrderRequest.class
        );

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private Order order(UUID orderId, OrderStatus status, PaymentStatus paymentStatus) {
        Order order = Order.builder()
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
        order.setId(orderId);
        return order;
    }
}
