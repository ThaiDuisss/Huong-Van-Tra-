package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusManagementServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private OrderStatusManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStatusManagementServiceImpl(
                orderRepository,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void updateOrderStatusKeepsSaveHistoryAndResponseOrder() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setNote("Hoàn tất đơn");
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateOrderStatus(orderId, request);

        assertSame(expected, actual);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        InOrder inOrder = inOrder(orderRepository, orderStatusHistoryWriter, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(order, OrderStatus.PAYMENT_CONFIRMED, OrderStatus.COMPLETED, "Hoàn tất đơn", null);
        inOrder.verify(orderResponseAssembler).toResponse(order);
    }

    @Test
    void updateOrderStatusReturnsResponseWithoutSaveOrHistoryWhenStatusUnchanged() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.PENDING_PAYMENT);
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateOrderStatus(orderId, request);

        assertSame(expected, actual);
        InOrder inOrder = inOrder(orderRepository, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoInteractions(orderStatusHistoryWriter);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void updateOrderStatusKeepsExistingNotFoundMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.updateOrderStatus(orderId, request)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(orderStatusHistoryWriter, orderResponseAssembler);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void updateOrderStatusKeepsTransactionOnImplementation() throws NoSuchMethodException {
        Method method = OrderStatusManagementServiceImpl.class.getMethod(
                "updateOrderStatus",
                UUID.class,
                UpdateOrderStatusRequest.class
        );

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private Order order(UUID orderId, OrderStatus status) {
        Order order = Order.builder()
                .status(status)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .build();
        order.setId(orderId);
        return order;
    }
}
