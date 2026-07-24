package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCustomerService orderCustomerService;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    @Mock
    private OrderPaymentSagaService orderPaymentSagaService;

    @Spy
    private OrderValidator orderValidator = new OrderValidator();

    @Spy
    private OrderAmountCalculator orderAmountCalculator = new OrderAmountCalculator();

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private OrderPaymentEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentEventServiceImpl(
                orderRepository,
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void handlePaymentSucceededKeepsSuccessfulEventFlowAndSideEffectOrder() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        order.setPaymentMethod("CASH");
        order.setPaymentCode("OLD-CODE");
        PaymentOrderEventPayload event = event("payment.succeeded", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.normalizePaymentMethod("BANK")).thenReturn("BANK_TRANSFER");
        when(orderRepository.save(order)).thenReturn(order);
        when(orderPaymentSagaService.completePaymentSaga(order, event, event.getEventId())).thenReturn(sagaId);
        OrderPaymentEventServiceImpl serviceSpy = spy(service);

        serviceSpy.handlePaymentSucceeded(event);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("BANK_TRANSFER", order.getPaymentMethod());
        assertEquals("PAY-001", order.getPaymentCode());
        assertEquals(new BigDecimal("100.00"), order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());

        InOrder inOrder = inOrder(
                orderRepository,
                orderAmountCalculator,
                orderValidator,
                orderResponseAssembler,
                orderStatusHistoryWriter,
                orderPaymentSagaService,
                orderCustomerService,
                orderOutboxPublisher
        );
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderAmountCalculator).defaultZero(event.getAmountPaid());
        inOrder.verify(orderValidator).validatePaymentAmount(order, event.getAmountPaid());
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod("BANK");
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.COMPLETED,
                "Thanh toán chuyển khoản thành công, mã thanh toán: PAY-001",
                null
        );
        inOrder.verify(orderPaymentSagaService).completePaymentSaga(order, event, event.getEventId());
        inOrder.verify(orderCustomerService).updateCustomerPurchase(orderId);
        inOrder.verify(orderOutboxPublisher).publishPaymentCompletedOrchestration(
                order,
                event.getEventId(),
                sagaId.toString()
        );
        inOrder.verify(orderOutboxPublisher).publishOrderPaymentSucceededNotification(
                order,
                event,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.COMPLETED,
                sagaId.toString()
        );
        verify(serviceSpy).handlePaymentEvent(event);
        verify(orderRepository).save(order);
    }

    @Test
    void handlePaymentSucceededDelegatesNullToExistingNoOpFlow() {
        OrderPaymentEventServiceImpl serviceSpy = spy(service);

        serviceSpy.handlePaymentSucceeded(null);

        verify(serviceSpy).handlePaymentEvent(null);
        verifyNoInteractions(
                orderRepository,
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void nullBlankAndUnknownEventsKeepExistingNoOpBehavior() {
        PaymentOrderEventPayload missingOrderId = new PaymentOrderEventPayload();
        missingOrderId.setEventType("PAYMENT_SUCCEEDED");
        PaymentOrderEventPayload missingType = event(null, UUID.randomUUID());
        PaymentOrderEventPayload unknownType = event("PAYMENT_UNSUPPORTED", UUID.randomUUID());

        service.handlePaymentEvent(null);
        service.handlePaymentEvent(missingOrderId);
        service.handlePaymentEvent(missingType);
        service.handlePaymentEvent(unknownType);

        verifyNoInteractions(
                orderRepository,
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void eventKeepsNotFoundExceptionMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEventPayload event = event("PAYMENT_SUCCEEDED", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.handlePaymentEvent(event)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void alreadyPaidSuccessEventOnlyCompletesSaga() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.COMPLETED, PaymentStatus.PAID);
        PaymentOrderEventPayload event = event("PAYMENT_SUCCEEDED", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        service.handlePaymentEvent(event);

        verify(orderPaymentSagaService).completePaymentSaga(order, event, event.getEventId());
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(
                orderCustomerService,
                orderOutboxPublisher,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void successfulEventKeepsInvalidAmountExceptionAndStopsWriteFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        PaymentOrderEventPayload event = event("PAYMENT_SUCCEEDED", orderId);
        event.setAmountPaid(new BigDecimal("99.99"));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> service.handlePaymentEvent(event)
        );

        assertEquals("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng", exception.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void failedEventKeepsFailureFlowAndReasonOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        PaymentOrderEventPayload event = event("payment.failed", orderId);
        event.setFailureReason("Gateway declined");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.normalizePaymentMethod("BANK")).thenReturn("BANK_TRANSFER");
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        assertEquals(new BigDecimal("100.00"), order.getDebtAmount());
        InOrder inOrder = inOrder(
                orderRepository,
                orderResponseAssembler,
                orderStatusHistoryWriter,
                orderPaymentSagaService,
                orderOutboxPublisher
        );
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod("BANK");
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                "Gateway declined",
                null
        );
        inOrder.verify(orderPaymentSagaService).failPaymentSaga(order, event, "Gateway declined");
        inOrder.verify(orderOutboxPublisher).publishOrderPaymentFailedNotification(
                order,
                event,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                event.getSagaId(),
                "Gateway declined"
        );
        verifyNoInteractions(orderCustomerService);
    }

    @Test
    void expiredEventKeepsDefaultReasonAndFailedNotification() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        PaymentOrderEventPayload event = event("PAYMENT_EXPIRED", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.EXPIRED,
                "Thanh toán hết hạn",
                null
        );
        verify(orderPaymentSagaService).failPaymentSaga(order, event, "Thanh toán hết hạn");
        verify(orderOutboxPublisher).publishOrderPaymentFailedNotification(
                order,
                event,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.EXPIRED,
                event.getSagaId(),
                "Thanh toán hết hạn"
        );
        verify(orderOutboxPublisher, never()).publishOrderCancelledNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void cancelledEventKeepsCancelledNotificationFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        PaymentOrderEventPayload event = event("PAYMENT_CANCELLED", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELLED,
                "Thanh toán đã bị hủy",
                null
        );
        verify(orderPaymentSagaService).failPaymentSaga(order, event, "Thanh toán đã bị hủy");
        verify(orderOutboxPublisher).publishOrderCancelledNotification(
                order,
                event,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELLED,
                event.getSagaId(),
                "Thanh toán đã bị hủy"
        );
        verify(orderOutboxPublisher, never()).publishOrderPaymentFailedNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void needReviewEventKeepsSaveHistorySagaAndNoExternalCompletionFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_FAILED, PaymentStatus.FAILED);
        PaymentOrderEventPayload event = event("PAYMENT_NEED_REVIEW", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        assertEquals(new BigDecimal("100.00"), order.getDebtAmount());
        InOrder inOrder = inOrder(orderRepository, orderStatusHistoryWriter, orderPaymentSagaService);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.PENDING_PAYMENT,
                "Thanh toán cần kiểm tra thủ công",
                null
        );
        inOrder.verify(orderPaymentSagaService).markPaymentNeedReview(
                order,
                event,
                "Thanh toán cần kiểm tra thủ công"
        );
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher);
    }

    @Test
    void needReviewEventAlreadyPendingKeepsExistingHistoryNoOp() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        PaymentOrderEventPayload event = event("PAYMENT_NEED_REVIEW", orderId);
        event.setFailureReason("Manual review");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        verifyNoInteractions(orderStatusHistoryWriter);
        verify(orderPaymentSagaService).markPaymentNeedReview(order, event, "Manual review");
    }

    @Test
    void waitingEventKeepsSaveHistoryAndSagaOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_FAILED, PaymentStatus.FAILED);
        order.setPaymentMethod("CASH");
        PaymentOrderEventPayload event = event("payment.waiting-payment", orderId);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.handlePaymentEvent(event);

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        assertEquals("CASH", order.getPaymentMethod());
        InOrder inOrder = inOrder(orderRepository, orderStatusHistoryWriter, orderPaymentSagaService);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.PENDING_PAYMENT,
                "Đang chờ thanh toán",
                null
        );
        inOrder.verify(orderPaymentSagaService).markPaymentWaiting(
                order,
                event,
                "Đang chờ payment event thành công"
        );
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher);
    }

    @Test
    void paymentEventEntryPointsKeepTransactionalBoundariesOnDedicatedService() throws NoSuchMethodException {
        Method eventMethod = OrderPaymentEventServiceImpl.class.getMethod(
                "handlePaymentEvent",
                PaymentOrderEventPayload.class
        );
        Method succeededMethod = OrderPaymentEventServiceImpl.class.getMethod(
                "handlePaymentSucceeded",
                PaymentOrderEventPayload.class
        );
        Transactional eventTransactional = eventMethod.getAnnotation(Transactional.class);
        Transactional succeededTransactional = succeededMethod.getAnnotation(Transactional.class);

        assertNotNull(eventTransactional);
        assertFalse(eventTransactional.readOnly());
        assertEquals(Propagation.REQUIRED, eventTransactional.propagation());
        assertNotNull(succeededTransactional);
        assertFalse(succeededTransactional.readOnly());
        assertEquals(Propagation.REQUIRED, succeededTransactional.propagation());
        assertEquals(0, succeededTransactional.rollbackFor().length);
        assertEquals(0, succeededTransactional.noRollbackFor().length);
        assertNull(OrderServiceImpl.class
                .getMethod("handlePaymentEvent", PaymentOrderEventPayload.class)
                .getAnnotation(Transactional.class));
        assertNull(OrderServiceImpl.class
                .getMethod("handlePaymentSucceeded", PaymentOrderEventPayload.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void dedicatedPaymentEventServiceDoesNotDependOnOrderFacade() {
        for (Field field : OrderPaymentEventServiceImpl.class.getDeclaredFields()) {
            assertFalse(OrderService.class.isAssignableFrom(field.getType()));
            assertFalse(OrderServiceImpl.class.isAssignableFrom(field.getType()));
        }
    }

    private Order order(UUID orderId, OrderStatus status, PaymentStatus paymentStatus) {
        Order order = Order.builder()
                .status(status)
                .paymentStatus(paymentStatus)
                .finalAmount(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(new BigDecimal("100.00"))
                .build();
        order.setId(orderId);
        return order;
    }

    private PaymentOrderEventPayload event(String eventType, UUID orderId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setEventId("event-001");
        event.setEventKey("event-key-001");
        event.setEventType(eventType);
        event.setSagaId("saga-001");
        event.setOrderId(orderId.toString());
        event.setPaymentId("payment-001");
        event.setPaymentCode("PAY-001");
        event.setPaymentMethod("BANK");
        event.setAmountPaid(new BigDecimal("100.00"));
        return event;
    }
}
