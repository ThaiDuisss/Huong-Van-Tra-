package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.BackorderAvailabilityResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.OrderBackorderSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderBackorderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderBackorderSupport orderBackorderSupport;
    @Mock
    private OrderPaymentEventService orderPaymentEventService;
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
    private OrderMapper orderMapper;
    @Mock
    private PaymentClient paymentClient;

    private OrderBackorderServiceImpl service;
    private UUID orderId;
    private UUID sagaId;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderBackorderServiceImpl(orderRepository, orderBackorderSupport, orderPaymentEventService,
                orderPaymentSagaService, orderCustomerService, orderOutboxPublisher, orderStatusHistoryWriter,
                orderResponseAssembler, orderMapper, paymentClient);
        orderId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
        order = Order.builder()
                .orderCode("ORD-001")
                .orderType("POS")
                .status(OrderStatus.PAYMENT_CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .inventoryProcessingStatus(OrderInventoryProcessingStatus.WAITING_STOCK)
                .backorder(true)
                .paymentMethod("CASH")
                .paymentCode("PAY-001")
                .originalAmount(new BigDecimal("100000"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("100000"))
                .paidAmount(new BigDecimal("100000"))
                .debtAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderBackorderSupport.estimate(order)).thenReturn(estimate(false));
        when(orderPaymentSagaService.completePaymentSaga(any(), any(), any())).thenReturn(sagaId);
        when(orderPaymentSagaService.startPaymentSaga(any(), any())).thenReturn(sagaId);
        inventoryDeductionEndsWith(OrderInventoryProcessingStatus.CONSUMED);
    }

    @Test
    void fulfillPaidBackorderDeductsStockAndCompletesOrder() {
        service.fulfill(orderId, null);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderInventoryProcessingStatus.CONSUMED, order.getInventoryProcessingStatus());
        verify(orderPaymentEventService).processPaidOrderInventory(order, null, true);
        verifyNoInteractions(paymentClient);
        verify(orderCustomerService).updateCustomerPurchase(orderId);
        verify(orderOutboxPublisher).publishPaymentCompletedOrchestration(order, "PAY-001", sagaId.toString());
        verify(orderStatusHistoryWriter).write(eq(order), eq(OrderStatus.PAYMENT_CONFIRMED),
                eq(OrderStatus.COMPLETED), any(), any());
    }

    @Test
    void fulfillDepositedBackorderDeductsStockBeforeCollectingRemainder() {
        deposited("30000");
        PaymentResponse paid = PaymentResponse.builder().status("PAID").method("CASH")
                .changeAmount(new BigDecimal("10000")).build();
        when(paymentClient.checkout(any(PaymentCreateRequest.class))).thenReturn(paid);
        FulfillBackorderRequest request = new FulfillBackorderRequest();
        request.setReceivedAmount(new BigDecimal("80000"));

        service.fulfill(orderId, request);

        InOrder inOrder = inOrder(orderPaymentEventService, paymentClient);
        inOrder.verify(orderPaymentEventService).processPaidOrderInventory(order, null, true);
        ArgumentCaptor<PaymentCreateRequest> captor = ArgumentCaptor.forClass(PaymentCreateRequest.class);
        inOrder.verify(paymentClient).checkout(captor.capture());
        assertEquals(PaymentMethod.CASH, captor.getValue().getPaymentMethod());
        assertEquals(new BigDecimal("100000"), captor.getValue().getFinalAmount());
        assertEquals(new BigDecimal("80000"), captor.getValue().getReceivedAmount());
        assertNull(captor.getValue().getDepositAmount());
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(new BigDecimal("100000"), order.getPaidAmount());
        assertEquals(0, order.getDebtAmount().signum());
        verify(orderOutboxPublisher).publishPaymentCompletedOrchestration(order, "PAY-001", sagaId.toString());
    }

    @Test
    void fulfillRejectsWhenCustomerGivesLessThanRemainder() {
        deposited("30000");
        FulfillBackorderRequest request = new FulfillBackorderRequest();
        request.setReceivedAmount(new BigDecimal("50000"));

        assertThrows(InvalidPaymentAmountException.class, () -> service.fulfill(orderId, request));
        verify(orderPaymentEventService, never()).processPaidOrderInventory(any(), any(), eq(true));
        verifyNoInteractions(paymentClient);
    }

    @Test
    void fulfillRejectsWhenStockStillMissing() {
        when(orderBackorderSupport.estimate(order)).thenReturn(estimate(true));

        InvalidOrderStatusException exception =
                assertThrows(InvalidOrderStatusException.class, () -> service.fulfill(orderId, null));

        assertTrue(exception.getMessage().contains("Trà sen"));
        verify(orderPaymentEventService, never()).processPaidOrderInventory(any(), any(), eq(true));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
    }

    @Test
    void fulfillDoesNotCollectMoneyWhenStockDeductionFails() {
        deposited("30000");
        inventoryDeductionEndsWith(OrderInventoryProcessingStatus.FAILED);
        FulfillBackorderRequest request = new FulfillBackorderRequest();
        request.setReceivedAmount(new BigDecimal("70000"));

        assertThrows(InvalidOrderStatusException.class, () -> service.fulfill(orderId, request));
        verifyNoInteractions(paymentClient);
        verify(orderOutboxPublisher, never()).publishPaymentCompletedOrchestration(any(), any(), any());
    }

    @Test
    void fulfillRejectsOrderThatIsNotWaitingForStock() {
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);

        assertThrows(InvalidOrderStatusException.class, () -> service.fulfill(orderId, null));
    }

    @Test
    void checkAvailabilityRefreshesExpectedDateWhileStillWaiting() {
        Instant eta = Instant.parse("2026-10-09T03:00:00Z");
        when(orderBackorderSupport.prepare(order)).thenReturn(
                new InventoryClient.AvailabilityEstimateResponse(true, eta, List.of()));

        BackorderAvailabilityResponse response = service.checkAvailability(orderId);

        assertFalse(response.ready());
        assertEquals(eta, response.estimatedAvailableAt());
        assertEquals(eta, order.getExpectedAvailableAt());
        verify(orderRepository).save(order);
    }

    @Test
    void checkAvailabilityReportsReadyWhenStockIsBack() {
        when(orderBackorderSupport.prepare(order)).thenReturn(estimate(false));

        BackorderAvailabilityResponse response = service.checkAvailability(orderId);

        assertTrue(response.ready());
        assertNull(response.estimatedAvailableAt());
    }

    @ParameterizedTest @ValueSource(strings = {"null-response", "missing-status", "WAITING_PAYMENT", "FAILED"})
    void incompleteSettlementNeverCompletesDepositedBackorderOrNotifiesCustomer(String failure) {
        deposited("30000");
        PaymentResponse result = failure.equals("null-response") ? null : PaymentResponse.builder()
                .status(failure.equals("missing-status") ? null : failure).build();
        when(paymentClient.checkout(any())).thenReturn(result);
        FulfillBackorderRequest request = new FulfillBackorderRequest();
        request.setReceivedAmount(new BigDecimal("70000"));
        assertThrows(InvalidPaymentAmountException.class, () -> service.fulfill(orderId, request));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_PAID, order.getPaymentStatus());
        assertEquals(new BigDecimal("30000"), order.getPaidAmount());
        assertEquals(new BigDecimal("70000"), order.getDebtAmount());
        verify(paymentClient, times(1)).checkout(any());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderCustomerService, orderOutboxPublisher, orderStatusHistoryWriter, orderResponseAssembler);
        verify(orderPaymentSagaService, never()).markResourceConfirmationRequested(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,true", "true,false,false", "true,true,true"})
    void availabilityKeepsBundlePackingSeparateFromProductReadiness(boolean productMissing, boolean bundlePending, boolean expectedPending) {
        Instant eta = Instant.parse("2026-11-04T00:00:00Z");
        order.setExpectedAvailableAt(eta);
        when(orderBackorderSupport.prepare(order)).thenReturn(new InventoryClient.AvailabilityEstimateResponse(productMissing, eta, null));
        when(orderBackorderSupport.hasBundle(order)).thenReturn(true);
        when(orderBackorderSupport.hasUnpackedBundle(order)).thenReturn(bundlePending);
        var result = service.checkAvailability(orderId);
        assertEquals(!productMissing && !expectedPending, result.ready());
        assertEquals(result.ready() ? null : eta, result.estimatedAvailableAt());
        assertTrue(result.lines().isEmpty());
        verify(orderBackorderSupport).refreshBundles(order);
        verifyNoInteractions(paymentClient, orderCustomerService, orderOutboxPublisher);
    }

    @Test void nullAvailabilityIsUnknownAndDoesNotEraseStoredEtaOrCollectMoney() {
        Instant eta = Instant.parse("2026-11-04T00:00:00Z");
        order.setExpectedAvailableAt(eta);
        when(orderBackorderSupport.prepare(order)).thenReturn(null);
        var result = service.checkAvailability(orderId);
        assertFalse(result.ready());
        assertNull(result.estimatedAvailableAt());
        assertEquals(eta, order.getExpectedAvailableAt());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentClient, orderOutboxPublisher);
    }

    @ParameterizedTest @ValueSource(strings = {"UNPAID", "PARTIALLY_PAID", "WAITING_PAYMENT"})
    void absentOrZeroDebtDoesNotAllowUnsettledOrderToBeHandedOver(String payment) {
        order.setPaymentStatus(PaymentStatus.valueOf(payment));
        order.setDebtAmount(null);
        assertThrows(InvalidOrderStatusException.class, () -> service.fulfill(orderId, null));
        verifyNoInteractions(paymentClient, orderCustomerService, orderOutboxPublisher);
        verify(orderPaymentEventService, never()).processPaidOrderInventory(any(), any(), anyBoolean());
        verify(orderRepository, never()).save(any());
    }

    private void deposited(String deposit) {
        order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        order.setPaidAmount(new BigDecimal(deposit));
        order.setDepositAmount(new BigDecimal(deposit));
        order.setDebtAmount(order.getFinalAmount().subtract(new BigDecimal(deposit)));
    }

    private void inventoryDeductionEndsWith(OrderInventoryProcessingStatus status) {
        doAnswer(invocation -> {
            Order target = invocation.getArgument(0);
            target.setInventoryProcessingStatus(status);
            return null;
        }).when(orderPaymentEventService).processPaidOrderInventory(any(), any(), eq(true));
    }

    private InventoryClient.AvailabilityEstimateResponse estimate(boolean backorder) {
        return new InventoryClient.AvailabilityEstimateResponse(backorder, null, List.of(
                new InventoryClient.AvailabilityLineEstimate(UUID.randomUUID(), "Trà sen", new BigDecimal("5"),
                        backorder ? new BigDecimal("2") : BigDecimal.ZERO,
                        backorder ? "IN_PRODUCTION" : "AVAILABLE", null, null, null, false, "")));
    }
}
