package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutPaymentProcessorTest {

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderPaymentSagaService orderPaymentSagaService;

    @Mock
    private OrderCustomerService orderCustomerService;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    @Mock
    private OrderPaymentEventService orderPaymentEventService;

    @Mock
    private OrderBackorderSupport orderBackorderSupport;

    private OrderCheckoutPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderCheckoutPaymentProcessor(
                paymentClient,
                orderValidator,
                orderPaymentSagaService,
                orderCustomerService,
                orderOutboxPublisher,
                orderStatusHistoryWriter,
                orderResponseAssembler,
                orderPaymentEventService,
                orderBackorderSupport
        );
    }

    @Test
    void checkoutPaymentPaidKeepsSagaPaymentHistoryCustomerAndOutboxOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Order order = order(orderId, customerId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentCode("PAY-OLD");
        PaymentResponse payment = PaymentResponse.builder()
                .paymentId(UUID.randomUUID())
                .paymentCode("PAY-001")
                .status("PAID")
                .method("cash")
                .amountRequired(order.getFinalAmount())
                .amountPaid(order.getFinalAmount())
                .build();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderResponseAssembler.normalizePaymentMethod("cash")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);

        PaymentResponse result = processor.checkoutPayment(order, "CASH", new BigDecimal("100000.00"), changedBy);

        assertSame(payment, result);
        // Processor chỉ ghi nhận bước "Paid"; trừ kho + hoàn tất đơn do finalizePaidOrder đảm nhiệm.
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals("PAY-001", order.getPaymentCode());
        assertEquals(order.getFinalAmount(), order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());
        ArgumentCaptor<PaymentCreateRequest> requestCaptor = ArgumentCaptor.forClass(PaymentCreateRequest.class);
        InOrder inOrder = inOrder(
                orderValidator,
                orderPaymentSagaService,
                paymentClient,
                orderResponseAssembler,
                orderStatusHistoryWriter,
                orderPaymentEventService
        );
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod("CASH");
        inOrder.verify(orderValidator).validateCashPaymentAmount(order, PaymentMethod.CASH, new BigDecimal("100000.00"));
        inOrder.verify(orderPaymentSagaService).startPaymentSaga(order, PaymentMethod.CASH.name());
        inOrder.verify(paymentClient).checkout(requestCaptor.capture());
        inOrder.verify(orderValidator).validatePaymentCheckoutResult(payment);
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod("cash");
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_CONFIRMED,
                "Đã thanh toán bằng CASH, mã thanh toán: PAY-001",
                changedBy
        );
        inOrder.verify(orderPaymentEventService).finalizePaidOrder(
                org.mockito.Mockito.eq(order),
                any(PaymentOrderEventPayload.class),
                org.mockito.Mockito.eq(OrderStatus.PENDING_PAYMENT),
                org.mockito.Mockito.eq(changedBy),
                org.mockito.Mockito.eq("PAY-001")
        );
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher);
        PaymentCreateRequest paymentRequest = requestCaptor.getValue();
        assertEquals(orderId, paymentRequest.getOrderId());
        assertEquals(sagaId, paymentRequest.getSagaId());
        assertEquals(PaymentMethod.CASH, paymentRequest.getPaymentMethod());
        assertEquals(new BigDecimal("100000.00"), paymentRequest.getReceivedAmount());
        assertEquals(false, paymentRequest.getDeliveryRequired());
    }

    @Test
    void deliveryCashPaymentKeepsMaterialsReservedUntilFulfillment() {
        Order order = order(UUID.randomUUID(), null);
        order.setOrderType("DELIVERY");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-DELIVERY")
                .status("PAID")
                .method("cash")
                .build();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderResponseAssembler.normalizePaymentMethod("cash")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(UUID.randomUUID());
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);

        processor.checkoutPayment(order, "CASH", order.getFinalAmount(), null);

        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verify(orderPaymentEventService).finalizePaidOrder(org.mockito.Mockito.eq(order),
                any(PaymentOrderEventPayload.class), org.mockito.Mockito.eq(OrderStatus.PENDING_PAYMENT),
                org.mockito.Mockito.isNull(), org.mockito.Mockito.eq("PAY-DELIVERY"));
    }

    @Test
    void checkoutPaymentWaitingKeepsRequestedSagaAndNoCustomerOrOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Order order = order(orderId, null);
        order.setStatus(OrderStatus.DRAFT);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-002")
                .status("WAITING_PAYMENT")
                .method("bank")
                .build();
        when(orderResponseAssembler.normalizePaymentMethod("BANK_TRANSFER")).thenReturn("BANK_TRANSFER");
        when(orderResponseAssembler.normalizePaymentMethod("bank")).thenReturn("BANK_TRANSFER");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.BANK_TRANSFER.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);

        PaymentResponse result = processor.checkoutPayment(order, "BANK_TRANSFER", null, null);

        assertSame(payment, result);
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(order.getFinalAmount(), order.getDebtAmount());
        InOrder inOrder = inOrder(orderStatusHistoryWriter, orderPaymentSagaService);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                OrderStatus.DRAFT,
                OrderStatus.PENDING_PAYMENT,
                "Tạo yêu cầu thanh toán BANK_TRANSFER, mã thanh toán: PAY-002",
                null
        );
        inOrder.verify(orderPaymentSagaService).markPaymentRequested(order, sagaId, payment);
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher);
    }

    @Test
    void checkoutPaymentRejectsUnsupportedMethodBeforeSagaOrPaymentClient() {
        Order order = order(UUID.randomUUID(), null);
        when(orderResponseAssembler.normalizePaymentMethod("CARD")).thenReturn("CARD");

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> processor.checkoutPayment(order, "CARD", null, null)
        );

        assertEquals("Payment-Service chưa hỗ trợ phương thức thanh toán: CARD", exception.getMessage());
        verifyNoInteractions(orderValidator, orderPaymentSagaService, paymentClient);
    }

    @Test
    void paidBackorderWaitsForStockInsteadOfCompleting() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Order order = order(orderId, UUID.randomUUID());
        order.setBackorder(true);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-001").status("PAID").method("CASH")
                .amountRequired(order.getFinalAmount()).amountPaid(order.getFinalAmount()).build();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);
        when(orderBackorderSupport.isAwaitingStock(order)).thenReturn(true);

        processor.checkoutPayment(order, "CASH", new BigDecimal("80000.00"), null);

        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(0, order.getDebtAmount().signum());
        verify(orderBackorderSupport).enterWaitingStock(order);
        verify(orderPaymentSagaService).completePaymentSaga(any(Order.class), any(PaymentOrderEventPayload.class), org.mockito.Mockito.eq("PAY-001"));
        verifyNoInteractions(orderPaymentEventService, orderCustomerService);
        verify(orderOutboxPublisher, org.mockito.Mockito.never())
                .publishPaymentCompletedOrchestration(any(), any(), any());
    }

    @Test
    void depositOnBackorderKeepsRemainingAsDebt() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Order order = order(orderId, null);
        order.setBackorder(true);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-002").status("PARTIALLY_PAID").method("CASH")
                .amountRequired(order.getFinalAmount()).amountPaid(new BigDecimal("30000")).build();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);
        when(orderBackorderSupport.isAwaitingStock(order)).thenReturn(true);

        processor.checkoutPayment(order, "CASH", new BigDecimal("50000"), null, new BigDecimal("30000"));

        ArgumentCaptor<PaymentCreateRequest> captor = ArgumentCaptor.forClass(PaymentCreateRequest.class);
        verify(paymentClient).checkout(captor.capture());
        assertEquals(new BigDecimal("30000"), captor.getValue().getDepositAmount());
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(new BigDecimal("30000"), order.getPaidAmount());
        assertEquals(new BigDecimal("30000"), order.getDepositAmount());
        assertEquals(new BigDecimal("50000.00"), order.getDebtAmount());
        verify(orderBackorderSupport).enterWaitingStock(order);
        verify(orderPaymentSagaService).markPaymentRequested(order, sagaId, payment);
        verifyNoInteractions(orderPaymentEventService, orderCustomerService);
        // Thu cọc vẫn báo POS và đánh dấu mã giảm giá đã dùng; không phát ORDER_COMPLETED.
        verify(orderOutboxPublisher).publishCouponUsageCommit(order, "PAY-002", sagaId.toString());
        verify(orderOutboxPublisher).publishOrderPaymentSucceededNotification(
                org.mockito.Mockito.eq(order), any(PaymentOrderEventPayload.class),
                any(), org.mockito.Mockito.eq(OrderStatus.PAYMENT_CONFIRMED), org.mockito.Mockito.eq(sagaId.toString()));
        verify(orderOutboxPublisher, org.mockito.Mockito.never()).publishPaymentCompletedOrchestration(any(), any(), any());
    }

    @Test
    void backorderCheckoutMarksOrderWithExpectedDateFromInventory() {
        UUID sagaId = UUID.randomUUID();
        java.time.Instant eta = java.time.Instant.parse("2026-10-08T03:00:00Z");
        Order order = order(UUID.randomUUID(), null);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-003").status("PARTIALLY_PAID").method("CASH")
                .amountPaid(new BigDecimal("30000")).build();
        when(orderBackorderSupport.estimate(order)).thenReturn(
                new InventoryClient.AvailabilityEstimateResponse(true, eta, java.util.List.of()));
        when(orderBackorderSupport.isAwaitingStock(order)).thenReturn(true);
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);

        processor.checkoutBackorderPayment(order, "CASH", new BigDecimal("30000"), null, new BigDecimal("30000"));

        assertEquals(true, order.isBackorder());
        assertEquals(eta, order.getExpectedAvailableAt());
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.getPaymentStatus());
    }

    @Test
    void backorderCheckoutWithEnoughStockSellsAsNormalOrder() {
        UUID sagaId = UUID.randomUUID();
        Order order = order(UUID.randomUUID(), null);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-004").status("PAID").method("CASH").build();
        when(orderBackorderSupport.estimate(order)).thenReturn(
                new InventoryClient.AvailabilityEstimateResponse(false, null, java.util.List.of()));
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name())).thenReturn(sagaId);
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(payment);

        processor.checkoutBackorderPayment(order, "CASH", new BigDecimal("80000.00"), null, null);

        assertEquals(false, order.isBackorder());
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(orderPaymentEventService).finalizePaidOrder(
                org.mockito.Mockito.eq(order), any(PaymentOrderEventPayload.class), any(), any(), any());
    }

    @Test
    void depositRejectedWhenOrderHasEnoughStock() {
        Order order = order(UUID.randomUUID(), null);
        when(orderBackorderSupport.estimate(order)).thenReturn(
                new InventoryClient.AvailabilityEstimateResponse(false, null, java.util.List.of()));

        assertThrows(InvalidPaymentAmountException.class, () -> processor.checkoutBackorderPayment(
                order, "CASH", new BigDecimal("30000"), null, new BigDecimal("30000")));
        verifyNoInteractions(paymentClient);
    }

    @Test
    void depositRejectedWhenCustomerGivesLessThanDeposit() {
        Order order = order(UUID.randomUUID(), null);
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");

        assertThrows(InvalidPaymentAmountException.class,
                () -> processor.checkoutPayment(order, "CASH", new BigDecimal("20000"), null, new BigDecimal("30000")));
        verifyNoInteractions(paymentClient);
    }

    @Test
    void depositRejectedForBankTransfer() {
        Order order = order(UUID.randomUUID(), null);
        when(orderResponseAssembler.normalizePaymentMethod("BANK_TRANSFER")).thenReturn("BANK_TRANSFER");

        assertThrows(InvalidPaymentAmountException.class,
                () -> processor.checkoutPayment(order, "BANK_TRANSFER", null, null, new BigDecimal("30000")));
        verifyNoInteractions(paymentClient);
    }

    @ParameterizedTest @ValueSource(strings = {"FAILED", "DECLINED", "UNKNOWN", " "})
    void failedOrUnknownPaymentResultCannotCompleteOrderOrConfirmResources(String status) {
        Order order = order(UUID.randomUUID(), null);
        UUID saga = UUID.randomUUID();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, "CASH")).thenReturn(saga);
        PaymentResponse failed = PaymentResponse.builder().status(status).method("CASH").paymentCode("PAY-FAILED").build();
        when(paymentClient.checkout(any())).thenReturn(failed);
        assertSame(failed, processor.checkoutPayment(order, "CASH", order.getFinalAmount(), null));
        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        assertEquals(new BigDecimal("80000.00"), order.getDebtAmount());
        ArgumentCaptor<PaymentOrderEventPayload> event = ArgumentCaptor.forClass(PaymentOrderEventPayload.class);
        verify(orderPaymentSagaService).failPaymentSaga(eq(order), event.capture(), contains(status.trim()));
        assertEquals(saga.toString(), event.getValue().getSagaId());
        assertEquals(order.getId().toString(), event.getValue().getOrderId());
        assertEquals("PAYMENT_FAILED", event.getValue().getEventType());
        verifyNoInteractions(orderPaymentEventService, orderOutboxPublisher, orderCustomerService);
        verify(orderPaymentSagaService, never()).markResourceConfirmationRequested(any(), any());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void absentPaymentOrStatusIsRejectedByRealValidatorWithoutCompletion(boolean absent) {
        Order order = order(UUID.randomUUID(), null);
        UUID saga = UUID.randomUUID();
        OrderCheckoutPaymentProcessor realGuard = new OrderCheckoutPaymentProcessor(paymentClient, new OrderValidator(),
                orderPaymentSagaService, orderCustomerService, orderOutboxPublisher, orderStatusHistoryWriter,
                orderResponseAssembler, orderPaymentEventService, orderBackorderSupport);
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, "CASH")).thenReturn(saga);
        when(paymentClient.checkout(any())).thenReturn(absent ? null : PaymentResponse.builder().build());
        assertThrows(InvalidPaymentAmountException.class, () -> realGuard.checkoutPayment(order, "CASH", order.getFinalAmount(), null));
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        verifyNoInteractions(orderStatusHistoryWriter, orderCustomerService, orderOutboxPublisher, orderPaymentEventService);
        verify(orderPaymentSagaService, never()).completePaymentSaga(any(), any(), any());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-1", "80000", "80000.01"})
    void invalidDepositOrMissingCashCannotStartSagaOrCallPayment(String value) {
        Order order = order(UUID.randomUUID(), null);
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        BigDecimal deposit = value == null ? new BigDecimal("30000") : new BigDecimal(value);
        BigDecimal received = value == null ? null : order.getFinalAmount();
        assertThrows(InvalidPaymentAmountException.class, () -> processor.checkoutPayment(order, "CASH", received, null, deposit));
        verifyNoInteractions(paymentClient, orderPaymentSagaService, orderStatusHistoryWriter, orderOutboxPublisher, orderPaymentEventService);
        assertEquals(OrderStatus.DRAFT, order.getStatus());
    }

    @Test
    void downstreamCheckoutTimeoutIsNotRetriedOrReportedAsPaid() {
        Order order = order(UUID.randomUUID(), null);
        UUID saga = UUID.randomUUID();
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderPaymentSagaService.startPaymentSaga(order, "CASH")).thenReturn(saga);
        IllegalStateException timeout = new IllegalStateException("unknown payment outcome");
        when(paymentClient.checkout(any())).thenThrow(timeout);
        assertSame(timeout, assertThrows(IllegalStateException.class, () -> processor.checkoutPayment(order, "CASH", order.getFinalAmount(), null)));
        verify(paymentClient, times(1)).checkout(any());
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        verifyNoInteractions(orderPaymentEventService, orderCustomerService, orderOutboxPublisher, orderStatusHistoryWriter);
    }

    private Order order(UUID orderId, UUID customerId) {
        Order order = Order.builder()
                .orderCode("ORD-001")
                .customerId(customerId)
                .status(OrderStatus.DRAFT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(new BigDecimal("90000.00"))
                .discountAmount(new BigDecimal("10000.00"))
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("80000.00"))
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(new BigDecimal("80000.00"))
                .build();
        order.setId(orderId);
        return order;
    }
}
