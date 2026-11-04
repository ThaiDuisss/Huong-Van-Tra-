package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceImplTest {

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

    @Mock
    private OrderPaymentEventService orderPaymentEventService;

    @Mock
    private PaymentClient paymentClient;

    private OrderPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentServiceImpl(
                orderRepository,
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler,
                orderPaymentEventService,
                paymentClient
        );
    }

    @Test
    void markOrderPaidKeepsAmountsStatusesAndSideEffectOrder() {
        UUID orderId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        MarkOrderPaidRequest request = request(new BigDecimal("120.00"), "PAY-001", changedBy);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.markOrderPaid(orderId, request);

        assertSame(expected, actual);
        // Bước "Paid" ghi ở đây; trừ kho + Completed do finalizePaidOrder.
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(new BigDecimal("100.00"), order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());

        InOrder inOrder = inOrder(
                orderRepository,
                orderAmountCalculator,
                orderValidator,
                orderStatusHistoryWriter,
                paymentClient,
                orderPaymentEventService,
                orderResponseAssembler
        );
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderAmountCalculator).defaultZero(request.getPaidAmount());
        inOrder.verify(orderValidator).validateMarkPaid(order, request.getPaidAmount());
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_CONFIRMED,
                "Thanh toán thành công, mã thanh toán: PAY-001",
                changedBy
        );
        inOrder.verify(paymentClient).confirmManualPayment(orderId,
                new PaymentClient.ManualPaymentConfirmRequest("PAY-001", "Xác nhận thủ công"));
        inOrder.verify(orderPaymentEventService).finalizePaidOrder(order, null, OrderStatus.PENDING_PAYMENT, changedBy, "PAY-001");
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher, orderPaymentSagaService);
    }

    @Test
    void cancelPosPaymentClosesPaymentSessionAndReturnsOrderToDraft() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderPaymentEventService.returnOrderToDraft(any(), any())).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.cancelPosPayment(orderId);

        assertSame(expected, actual);
        InOrder inOrder = inOrder(paymentClient, orderPaymentEventService, orderPaymentSagaService);
        inOrder.verify(paymentClient).cancelPendingPayment(orderId);
        inOrder.verify(orderPaymentEventService).returnOrderToDraft(
                order, "Thu ngân huỷ thanh toán chuyển khoản, chọn lại phương thức");
        inOrder.verify(orderPaymentSagaService).failPaymentSaga(order, null, "Thu ngân huỷ phiên thanh toán");
    }

    @Test
    void cancelPosPaymentRejectsOrderWithoutWaitingPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.DRAFT, PaymentStatus.UNPAID);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStatusException.class, () -> service.cancelPosPayment(orderId));
        verifyNoInteractions(paymentClient, orderPaymentEventService);
    }

    @Test
    void markOrderPaidKeepsNotFoundExceptionMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.markOrderPaid(orderId, request(BigDecimal.TEN, "PAY-002", null))
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(
                orderCustomerService,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void markOrderPaidKeepsAlreadyPaidValidationAndStopsWriteFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.COMPLETED, PaymentStatus.PAID);
        MarkOrderPaidRequest request = request(new BigDecimal("100.00"), "PAY-003", null);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        OrderAlreadyPaidException exception = assertThrows(
                OrderAlreadyPaidException.class,
                () -> service.markOrderPaid(orderId, request)
        );

        assertEquals("Đơn hàng đã được thanh toán", exception.getMessage());
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
    void markOrderPaidKeepsInvalidAmountValidationAndStopsWriteFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        MarkOrderPaidRequest request = request(new BigDecimal("99.99"), "PAY-004", null);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> service.markOrderPaid(orderId, request)
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
    void markOrderPaidMovesExistingTransactionalBoundaryToDedicatedService() throws NoSuchMethodException {
        Method extractedMethod = OrderPaymentServiceImpl.class.getMethod(
                "markOrderPaid",
                UUID.class,
                MarkOrderPaidRequest.class
        );
        Transactional transactional = extractedMethod.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertFalse(transactional.readOnly());
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertEquals(0, transactional.rollbackFor().length);
        assertEquals(0, transactional.noRollbackFor().length);
        assertNull(OrderServiceImpl.class
                .getMethod("markOrderPaid", UUID.class, MarkOrderPaidRequest.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void dedicatedPaymentServiceDoesNotDependOnOrderFacade() {
        for (Field field : OrderPaymentServiceImpl.class.getDeclaredFields()) {
            assertFalse(OrderService.class.isAssignableFrom(field.getType()));
            assertFalse(OrderServiceImpl.class.isAssignableFrom(field.getType()));
        }
    }

    @Test
    void reviewedManualPaymentClearsExpiryAndFinalizesEvenWhenPaymentConfirmationIsUnavailable() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Order order = order(id, OrderStatus.PENDING_PAYMENT, PaymentStatus.NEED_REVIEW);
        order.setExpiresAt(Instant.parse("2026-11-03T00:00:00Z"));
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        doThrow(new IllegalStateException("payment unavailable")).when(paymentClient).confirmManualPayment(eq(id), any());
        service.markOrderPaid(id, request(new BigDecimal("120.00"), "MANUAL-REVIEW", actor));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(new BigDecimal("100.00"), order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());
        assertNull(order.getExpiresAt());
        verify(orderPaymentEventService).finalizePaidOrder(order, null, OrderStatus.PENDING_PAYMENT, actor, "MANUAL-REVIEW");
        verify(orderStatusHistoryWriter).write(order, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_CONFIRMED,
                "Đã đối soát và xác nhận thanh toán, mã thanh toán: MANUAL-REVIEW", actor);
    }

    @Test
    void cancelPaymentFailureLeavesOrderWaitingAndDoesNotReturnDraftOrFailSaga() {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        RuntimeException failure = new IllegalStateException("payment timeout");
        doThrow(failure).when(paymentClient).cancelPendingPayment(id);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.cancelPosPayment(id)));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        verifyNoInteractions(orderPaymentEventService, orderPaymentSagaService, orderStatusHistoryWriter, orderResponseAssembler);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void paidOrderCannotCancelPaymentAndWaitingStatusOnWrongOrderStateIsRejected() {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.COMPLETED, PaymentStatus.PAID);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        assertThrows(OrderAlreadyPaidException.class, () -> service.cancelPosPayment(id));
        order.setStatus(OrderStatus.DRAFT);
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        assertThrows(InvalidOrderStatusException.class, () -> service.cancelPosPayment(id));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        assertThrows(InvalidOrderStatusException.class, () -> service.cancelPosPayment(id));
        verifyNoInteractions(paymentClient, orderPaymentEventService, orderPaymentSagaService);
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

    private MarkOrderPaidRequest request(BigDecimal paidAmount, String paymentCode, UUID changedBy) {
        MarkOrderPaidRequest request = new MarkOrderPaidRequest();
        request.setPaidAmount(paidAmount);
        request.setPaymentCode(paymentCode);
        request.setChangedBy(changedBy);
        return request;
    }
}
