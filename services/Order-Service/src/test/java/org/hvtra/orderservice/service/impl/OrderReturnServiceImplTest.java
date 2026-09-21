package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionLineRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.repository.OrderItemRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.service.OrderReturnRefundTransactionService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnDisplayStatusResolver;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgressResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderReturnItemRepository orderReturnItemRepository;

    @Mock
    private OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;

    @Mock
    private OrderReturnHistoryRepository orderReturnHistoryRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private ReturnRefundCalculator returnRefundCalculator;

    @Mock
    private OrderReturnHistoryWriter orderReturnHistoryWriter;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    @Mock
    private OrderReturnRefundTransactionService orderReturnRefundTransactionService;

    @Mock
    private OrderReturnInventoryCommandReconciliationWriter inventoryCommandReconciliationWriter;

    private OrderReturnServiceImpl service;

    private UUID orderId;
    private UUID orderItemId;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        service = new OrderReturnServiceImpl(
                orderRepository,
                orderItemRepository,
                orderReturnRepository,
                orderReturnItemRepository,
                orderReturnInventoryCommandRepository,
                orderReturnHistoryRepository,
                orderStatusHistoryRepository,
                orderCodeGenerator,
                new OrderReturnMapper(),
                inventoryClient,
                paymentClient,
                returnRefundCalculator,
                orderReturnHistoryWriter,
                orderOutboxPublisher,
                orderReturnRefundTransactionService,
                inventoryCommandReconciliationWriter,
                new OrderReturnPhysicalProgressResolver(
                        orderReturnRepository,
                        orderReturnInventoryCommandRepository
                ),
                new OrderReturnDisplayStatusResolver(),
                new OrderReturnRefundEligibilityResolver(),
                new OrderReturnPhysicalCompletionHandler(
                        orderReturnRepository,
                        orderRepository,
                        new OrderReturnPhysicalProgressResolver(
                                orderReturnRepository,
                                orderReturnInventoryCommandRepository
                        ),
                        orderReturnHistoryWriter,
                        orderOutboxPublisher
                )
        );
        orderId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
        order = order(OrderStatus.COMPLETED, PaymentStatus.PAID);
        orderItem = orderItem(order, orderItemId, new BigDecimal("3.000"));
        order.setItems(List.of(orderItem));
    }

    @Test
    void createReturnCompletedPaidOrderSucceeds() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET202609060001");
        when(orderReturnRepository.existsByReturnCode("RET202609060001")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
        );

        assertEquals("RET202609060001", response.getReturnCode());
        assertEquals(orderId, response.getOrderId());
        assertEquals(ReturnStatus.CREATED, response.getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("1.000"), response.getItems().getFirst().getQuantity());

        ArgumentCaptor<OrderReturn> captor = ArgumentCaptor.forClass(OrderReturn.class);
        verify(orderReturnRepository).save(captor.capture());
        assertEquals(ReturnStatus.CREATED, captor.getValue().getStatus());
        assertSame(orderItem, captor.getValue().getItems().getFirst().getOrderItem());
        verify(orderReturnHistoryWriter).writeIfAbsent(
                captor.getValue(),
                OrderReturnHistoryAction.CREATED,
                null,
                ReturnStatus.CREATED,
                null,
                null,
                null,
                null
        );
        verify(orderOutboxPublisher).publishReturnCreatedNotification(captor.getValue(), null);
    }

    @Test
    void createReturnRejectsPendingPaymentOrder() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order is not completed", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsCompletedUnpaidOrder() {
        order.setPaymentStatus(PaymentStatus.UNPAID);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order payment status is not eligible for return", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnAllowsPartiallyRefundedOrderWithRemainingQuantity() {
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        order.setReturnStatus(OrderReturnStatus.PARTIALLY_RETURNED);
        stubEligibleOrder(BigDecimal.ONE);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-PARTIAL-REFUND");
        when(orderReturnRepository.existsByReturnCode("RET-PARTIAL-REFUND")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals("RET-PARTIAL-REFUND", response.getReturnCode());
        assertEquals(new BigDecimal("2.000"), response.getItems().getFirst().getQuantity());
        verify(orderReturnItemRepository).sumReturnedQuantity(
                eq(orderItemId),
                argThat(statuses -> statuses.contains(ReturnStatus.CREATED)
                        && statuses.contains(ReturnStatus.RECEIVED)
                        && statuses.contains(ReturnStatus.COMPLETED)
                        && !statuses.contains(ReturnStatus.CANCELLED))
        );
    }

    @Test
    void createReturnRejectsPartiallyRefundedOrderWhenQuantityExceedsRemaining() {
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        order.setReturnStatus(OrderReturnStatus.PARTIALLY_RETURNED);
        stubEligibleOrder(new BigDecimal("1.000"));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "3.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsRefundedOrder() {
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Order payment status is not eligible for return", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsFullyReturnedOrder() {
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        order.setReturnStatus(OrderReturnStatus.FULLY_RETURNED);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Order has already been fully returned", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsOrderItemFromAnotherOrder() {
        stubEligibleOrderOnly();

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(UUID.randomUUID(), "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order item does not belong to order", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsZeroQuantity() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "0.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return quantity must be greater than 0", exception.getMessage());
    }

    @Test
    void createReturnRejectsQuantityGreaterThanPurchasedQuantity() {
        stubEligibleOrder(BigDecimal.ZERO);

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "4.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
    }

    @Test
    void createReturnAllowsPartialReturn() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-PARTIAL");
        when(orderReturnRepository.existsByReturnCode("RET-PARTIAL")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals(new BigDecimal("2.000"), response.getItems().getFirst().getQuantity());
        assertEquals(ReturnReason.QUALITY_ISSUE, response.getItems().getFirst().getReason());
    }

    @Test
    void createReturnSecondReturnCannotExceedRemainingQuantity() {
        stubEligibleOrder(new BigDecimal("2.000"));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
    }

    @Test
    void createReturnRejectsWhenNoRemainingQuantityAvailable() {
        stubEligibleOrder(new BigDecimal("3.000"));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("No remaining quantity available for return", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnAllowsChangeOfMindWithSealedCondition() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-SEALED");
        when(orderReturnRepository.existsByReturnCode("RET-SEALED")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
        );

        assertEquals(ReturnReason.CHANGE_OF_MIND, response.getItems().getFirst().getReason());
        assertEquals(ReturnCondition.SEALED, response.getItems().getFirst().getCondition());
    }

    @Test
    void createReturnRejectsChangeOfMindWithOpenedCondition() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Change-of-mind return requires sealed product", exception.getMessage());
    }

    @Test
    void createReturnAllowsQualityIssueWithOpenedCondition() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-QUALITY");
        when(orderReturnRepository.existsByReturnCode("RET-QUALITY")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals(ReturnCondition.OPENED, response.getItems().getFirst().getCondition());
    }

    @Test
    void createReturnRejectsOtherWithoutNote() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.OTHER, ReturnCondition.SEALED, " ")
                )
        );

        assertEquals("Note is required when reason is OTHER", exception.getMessage());
    }

    @Test
    void createReturnAllowsOtherWithNote() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-OTHER");
        when(orderReturnRepository.existsByReturnCode("RET-OTHER")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.OTHER, ReturnCondition.SEALED, "Khách báo lý do khác")
        );

        assertEquals("Khách báo lý do khác", response.getItems().getFirst().getNote());
    }

    @Test
    @Disabled("Return window validation is temporarily disabled per business policy")
    void createReturnRejectsExpiredReturnWindow() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(15 * 24 * 60 * 60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return window has expired", exception.getMessage());
    }

    @Test
    void createReturnRejectsDuplicateItemToAvoidOverReturnAcrossRequestLines() {
        stubEligibleOrderOnly();
        CreateOrderReturnRequest request = CreateOrderReturnRequest.builder()
                .items(List.of(
                        item(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null),
                        item(orderItemId, "1.000", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null)
                ))
                .build();

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(orderId, request)
        );

        assertEquals("Duplicate order item in return request", exception.getMessage());
    }

    @Test
    void receiveReturnMovesCreatedReturnToReceived() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.CREATED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturnResponse response = service.receiveReturn(returnId);

        assertEquals(ReturnStatus.RECEIVED, orderReturn.getStatus());
        assertEquals(ReturnStatus.RECEIVED, response.getStatus());
        verify(orderReturnRepository).save(orderReturn);
        verify(orderReturnHistoryWriter).writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.RECEIVED,
                ReturnStatus.CREATED,
                ReturnStatus.RECEIVED,
                null,
                "Store received returned goods",
                null,
                null
        );
        verify(orderOutboxPublisher).publishReturnReceivedNotification(
                orderReturn,
                ReturnStatus.CREATED,
                null
        );
    }

    @Test
    void cancelReturnMovesCreatedReturnToCancelled() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.CREATED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderReturnRepository.save(orderReturn)).thenReturn(orderReturn);

        OrderReturnResponse response = service.cancelReturn(returnId);

        assertEquals(ReturnStatus.CANCELLED, orderReturn.getStatus());
        assertEquals(ReturnStatus.CANCELLED, response.getStatus());
        verify(orderReturnRepository).save(orderReturn);
        verify(orderReturnHistoryWriter).writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.CANCELLED,
                ReturnStatus.CREATED,
                ReturnStatus.CANCELLED,
                null,
                "Return cancelled",
                null,
                null
        );
        verify(orderOutboxPublisher).publishReturnCancelledNotification(
                orderReturn,
                ReturnStatus.CREATED,
                null
        );
    }

    @Test
    void cancelReturnRejectsReceivedReturn() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.cancelReturn(returnId)
        );

        assertEquals("Only created returns can be cancelled", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void receiveReturnRejectsCancelledReturn() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.CANCELLED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.receiveReturn(returnId)
        );

        assertEquals("Only created returns can be received", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void receiveReturnRejectsReceivedReturnWhenCalledAgain() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.receiveReturn(returnId)
        );

        assertEquals("Only created returns can be received", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void refundReturnRejectsReturnThatIsNotReceivedWithInspectionMessage() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.CREATED);
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.refundReturn(returnId)
        );

        assertEquals("Return inventory inspection is not completed", exception.getMessage());
        verifyNoInteractions(inventoryClient, returnRefundCalculator);
        verify(paymentClient, never()).refundOrderReturn(any(), any());
    }

    @Test
    void refundReturnRejectsPendingQcWithRequiredMessage() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED);
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.refundReturn(returnId)
        );

        assertEquals("Return inventory inspection is not completed", exception.getMessage());
        verifyNoInteractions(returnRefundCalculator);
        verify(paymentClient, never()).refundOrderReturn(any(), any());
    }

    @Test
    void refundReturnCalculatesAmountAndCallsPaymentService() {
        UUID returnId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED);
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderReturnInventoryCommandRepository.sumQuantityByReturnIdAndCommandStatus(
                returnId,
                OrderReturnInventoryCommandStatus.PROCESSED
        )).thenReturn(List.<Object[]>of(new Object[]{
                orderReturn.getItems().getFirst().getId(),
                BigDecimal.ONE
        }));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));
        when(returnRefundCalculator.calculate(order, orderReturn, List.of(orderReturn)))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("120000")));
        when(paymentClient.lookupRefundSummaries(any())).thenReturn(List.of(
                new PaymentClient.PaymentRefundSummaryResponse(
                        returnId,
                        orderId,
                        paymentId,
                        null,
                        null,
                        BigDecimal.ZERO,
                        new BigDecimal("500000"),
                        "PENDING",
                        "PAID",
                        "CASH",
                        null
                )
        ));
        PaymentClient.PaymentRefundResponse refundResponse = new PaymentClient.PaymentRefundResponse(
                paymentId,
                orderId,
                transactionId,
                returnId,
                new BigDecimal("120000"),
                new BigDecimal("120000"),
                "PARTIALLY_REFUNDED",
                "CASH",
                null
        );
        when(paymentClient.refundOrderReturn(
                eq(orderId),
                eq(new PaymentClient.PaymentRefundRequest(returnId, new BigDecimal("120000")))
        )).thenReturn(refundResponse);

        OrderReturnRefundResponse expectedResponse = OrderReturnRefundResponse.builder()
                .returnId(returnId)
                .orderId(orderId)
                .paymentId(paymentId)
                .refundTransactionId(transactionId)
                .refundAmount(new BigDecimal("120000"))
                .totalAmountRefunded(new BigDecimal("120000"))
                .paymentStatus("PARTIALLY_REFUNDED")
                .returnStatus(ReturnStatus.COMPLETED)
                .orderPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED)
                .orderReturnStatus(OrderReturnStatus.PARTIALLY_RETURNED)
                .method("CASH")
                .build();
        when(orderReturnRefundTransactionService.applyRefundSuccess(eq(returnId), eq(refundResponse), any()))
                .thenReturn(expectedResponse);

        var response = service.refundReturn(returnId);

        assertEquals(returnId, response.getReturnId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(transactionId, response.getRefundTransactionId());
        assertEquals(new BigDecimal("120000"), response.getRefundAmount());
        assertEquals("PARTIALLY_REFUNDED", response.getPaymentStatus());
        assertEquals(ReturnStatus.COMPLETED, response.getReturnStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, response.getOrderPaymentStatus());
        assertEquals(OrderReturnStatus.PARTIALLY_RETURNED, response.getOrderReturnStatus());
        verify(orderReturnRefundTransactionService).applyRefundSuccess(eq(returnId), eq(refundResponse), any());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void refundReturnMarksOrderFullyReturnedWhenAllItemsCompleted() {
        UUID returnId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED, new BigDecimal("3.000"));
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderReturnInventoryCommandRepository.sumQuantityByReturnIdAndCommandStatus(
                returnId,
                OrderReturnInventoryCommandStatus.PROCESSED
        )).thenReturn(List.<Object[]>of(new Object[]{
                orderReturn.getItems().getFirst().getId(),
                new BigDecimal("3.000")
        }));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));
        when(returnRefundCalculator.calculate(order, orderReturn, List.of(orderReturn)))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("200000")));
        when(paymentClient.lookupRefundSummaries(any())).thenReturn(List.of(
                new PaymentClient.PaymentRefundSummaryResponse(
                        returnId,
                        orderId,
                        paymentId,
                        null,
                        null,
                        BigDecimal.ZERO,
                        new BigDecimal("500000"),
                        "PENDING",
                        "PAID",
                        "CASH",
                        null
                )
        ));
        PaymentClient.PaymentRefundResponse refundResponse = new PaymentClient.PaymentRefundResponse(
                paymentId,
                orderId,
                transactionId,
                returnId,
                new BigDecimal("200000"),
                new BigDecimal("200000"),
                "REFUNDED",
                "CASH",
                null
        );
        when(paymentClient.refundOrderReturn(
                eq(orderId),
                eq(new PaymentClient.PaymentRefundRequest(returnId, new BigDecimal("200000")))
        )).thenReturn(refundResponse);

        OrderReturnRefundResponse expectedResponse = OrderReturnRefundResponse.builder()
                .returnId(returnId)
                .orderId(orderId)
                .paymentId(paymentId)
                .refundTransactionId(transactionId)
                .refundAmount(new BigDecimal("200000"))
                .totalAmountRefunded(new BigDecimal("200000"))
                .paymentStatus("REFUNDED")
                .returnStatus(ReturnStatus.COMPLETED)
                .orderPaymentStatus(PaymentStatus.REFUNDED)
                .orderReturnStatus(OrderReturnStatus.FULLY_RETURNED)
                .method("CASH")
                .build();
        when(orderReturnRefundTransactionService.applyRefundSuccess(eq(returnId), eq(refundResponse), any()))
                .thenReturn(expectedResponse);

        var response = service.refundReturn(returnId);

        assertEquals(ReturnStatus.COMPLETED, response.getReturnStatus());
        assertEquals(PaymentStatus.REFUNDED, response.getOrderPaymentStatus());
        assertEquals(OrderReturnStatus.FULLY_RETURNED, response.getOrderReturnStatus());
    }

    @Test
    void refundReturnDoesNotCountReceivedOrCancelledReturnsForOrderReturnStatus() {
        UUID returnId = UUID.randomUUID();
        UUID receivedReturnId = UUID.randomUUID();
        UUID cancelledReturnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.RECEIVED, BigDecimal.ONE);
        OrderReturn receivedReturn = orderReturn(receivedReturnId, ReturnStatus.RECEIVED, new BigDecimal("2.000"));
        OrderReturn cancelledReturn = orderReturn(cancelledReturnId, ReturnStatus.CANCELLED, new BigDecimal("2.000"));
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderReturnInventoryCommandRepository.sumQuantityByReturnIdAndCommandStatus(
                returnId,
                OrderReturnInventoryCommandStatus.PROCESSED
        )).thenReturn(List.<Object[]>of(new Object[]{
                orderReturn.getItems().getFirst().getId(),
                BigDecimal.ONE
        }));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn, receivedReturn, cancelledReturn));
        when(returnRefundCalculator.calculate(order, orderReturn, List.of(orderReturn, receivedReturn, cancelledReturn)))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("120000")));
        when(paymentClient.lookupRefundSummaries(any())).thenReturn(List.of(
                new PaymentClient.PaymentRefundSummaryResponse(
                        returnId,
                        orderId,
                        UUID.randomUUID(),
                        null,
                        null,
                        BigDecimal.ZERO,
                        new BigDecimal("500000"),
                        "PENDING",
                        "PAID",
                        "CASH",
                        null
                )
        ));
        PaymentClient.PaymentRefundResponse refundResponse = new PaymentClient.PaymentRefundResponse(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                returnId,
                new BigDecimal("120000"),
                new BigDecimal("120000"),
                "PARTIALLY_REFUNDED",
                "CASH",
                null
        );
        when(paymentClient.refundOrderReturn(any(), any())).thenReturn(refundResponse);

        OrderReturnRefundResponse expectedResponse = OrderReturnRefundResponse.builder()
                .returnId(returnId)
                .orderId(orderId)
                .orderReturnStatus(OrderReturnStatus.PARTIALLY_RETURNED)
                .returnStatus(ReturnStatus.COMPLETED)
                .build();
        when(orderReturnRefundTransactionService.applyRefundSuccess(eq(returnId), eq(refundResponse), any()))
                .thenReturn(expectedResponse);

        var response = service.refundReturn(returnId);

        assertEquals(OrderReturnStatus.PARTIALLY_RETURNED, response.getOrderReturnStatus());
    }

    @Test
    void refundReturnCompletedReturnReturnsExistingRefundWithoutRefundingAgain() {
        UUID returnId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.COMPLETED);
        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));
        when(paymentClient.lookupRefundSummaries(any())).thenReturn(List.of(
                new PaymentClient.PaymentRefundSummaryResponse(
                        returnId,
                        orderId,
                        paymentId,
                        transactionId,
                        new BigDecimal("120000"),
                        new BigDecimal("120000"),
                        new BigDecimal("120000"),
                        "REFUNDED",
                        "PARTIALLY_REFUNDED",
                        "CASH",
                        null
                )
        ));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));

        var response = service.refundReturn(returnId);

        assertEquals(returnId, response.getReturnId());
        assertEquals(transactionId, response.getRefundTransactionId());
        assertEquals(ReturnStatus.COMPLETED, response.getReturnStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, response.getOrderPaymentStatus());
        assertEquals(OrderReturnStatus.PARTIALLY_RETURNED, response.getOrderReturnStatus());
        verify(paymentClient, never()).refundOrderReturn(any(), any());
    }

    @Test
    void cancelReturnRejectsCancelledReturnWhenCalledAgain() {
        UUID returnId = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(returnId, ReturnStatus.CANCELLED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.cancelReturn(returnId)
        );

        assertEquals("Only created returns can be cancelled", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnCountsCreatedAndReceivedReturnsAsPreviouslyReturnedQuantity() {
        stubEligibleOrder(new BigDecimal("2.000"));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
        verify(orderReturnItemRepository).sumReturnedQuantity(
                eq(orderItemId),
                argThat(statuses -> statuses.contains(ReturnStatus.CREATED)
                        && statuses.contains(ReturnStatus.RECEIVED)
                        && statuses.contains(ReturnStatus.COMPLETED)
                        && statuses.size() == 3)
        );
    }

    @Test
    void createReturnDoesNotCountCancelledReturnsAsPreviouslyReturnedQuantity() {
        stubEligibleOrder(new BigDecimal("2.000"));

        assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        verify(orderReturnItemRepository).sumReturnedQuantity(
                eq(orderItemId),
                argThat(statuses -> !statuses.contains(ReturnStatus.CANCELLED))
        );
    }

    @Test
    void createReturnAllowsFullQuantityAgainAfterCancelledReturnIsNotCounted() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-AFTER-CANCEL");
        when(orderReturnRepository.existsByReturnCode("RET-AFTER-CANCEL")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "3.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals(ReturnStatus.CREATED, response.getStatus());
        assertEquals(new BigDecimal("3.000"), response.getItems().getFirst().getQuantity());
    }

    @Test
    void getReturnsReturnsAllReturnsWhenNoFilter() {
        OrderReturnSummaryResponse summary = summary("RET-ALL", ReturnStatus.CREATED, 1L);
        when(orderReturnRepository.findReturnSummariesList(null, null, null, null))
                .thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, null, null, null, 0, 20);

        assertEquals(List.of(summary), result.getContent());
        assertEquals(1, result.getTotalElements());
        verify(orderReturnRepository).findReturnSummariesList(null, null, null, null);
    }

    @Test
    void getReturnsFiltersCreatedReturns() {
        assertStatusFilter(ReturnStatus.CREATED);
    }

    @Test
    void getReturnsFiltersReceivedReturns() {
        assertStatusFilter(ReturnStatus.RECEIVED);
    }

    @Test
    void getReturnsFiltersCancelledReturns() {
        assertStatusFilter(ReturnStatus.CANCELLED);
    }

    @Test
    void getReturnsSearchesByReturnCodePattern() {
        assertKeywordPattern("RET00001", "%ret00001%");
    }

    @Test
    void getReturnsSearchesByOrderCodePattern() {
        assertKeywordPattern("ORD00001", "%ord00001%");
    }

    @Test
    void getReturnsSearchesByCustomerPatternWhenSupportedByQuery() {
        assertKeywordPattern("Nguyen Van A", "%nguyen van a%");
    }

    @Test
    void getReturnsFiltersOneDayByReturnCreatedAt() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        Instant fromCreatedAt = startOfDay(date);
        Instant toCreatedBefore = startOfDay(date.plusDays(1));
        OrderReturnSummaryResponse summary = summary("RET-DAY", ReturnStatus.CREATED, 1L);
        when(orderReturnRepository.findReturnSummariesList(null, null, fromCreatedAt, toCreatedBefore))
                .thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, date, date, null, 0, 20);

        assertEquals(List.of(summary), result.getContent());
        verify(orderReturnRepository).findReturnSummariesList(null, null, fromCreatedAt, toCreatedBefore);
    }

    @Test
    void getReturnsFiltersMonthRangeByReturnCreatedAt() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);
        Instant fromCreatedAt = startOfDay(fromDate);
        Instant toCreatedBefore = startOfDay(toDate.plusDays(1));
        OrderReturnSummaryResponse summary = summary("RET-MONTH", ReturnStatus.RECEIVED, 1L);
        when(orderReturnRepository.findReturnSummariesList(null, null, fromCreatedAt, toCreatedBefore))
                .thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, fromDate, toDate, null, 0, 20);

        assertEquals(ReturnStatus.RECEIVED, result.getContent().getFirst().getStatus());
        verify(orderReturnRepository).findReturnSummariesList(null, null, fromCreatedAt, toCreatedBefore);
    }

    @Test
    void getReturnsReturnsEmptyPageWhenDateRangeHasNoData() {
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);
        when(orderReturnRepository.findReturnSummariesList(
                null,
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        )).thenReturn(List.of());

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, fromDate, toDate, null, 0, 20);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getReturnsCombinesDateAndStatusFilters() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);
        OrderReturnSummaryResponse summary = summary("RET-STATUS-DATE", ReturnStatus.RECEIVED, 1L);
        when(orderReturnRepository.findReturnSummariesList(
                null,
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        )).thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result =
                service.getReturns(null, ReturnStatus.RECEIVED, fromDate, toDate, null, 0, 20);

        assertEquals(ReturnStatus.RECEIVED, result.getContent().getFirst().getStatus());
        verify(orderReturnRepository).findReturnSummariesList(
                null,
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        );
    }

    @Test
    void getReturnsCombinesDateAndKeywordFilters() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);
        OrderReturnSummaryResponse summary = summary("RET-KEYWORD-DATE", ReturnStatus.CREATED, 1L);
        when(orderReturnRepository.findReturnSummariesList(
                "%rtn%",
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        )).thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result = service.getReturns("RTN", null, fromDate, toDate, null, 0, 20);

        assertEquals("RET-KEYWORD-DATE", result.getContent().getFirst().getReturnCode());
        verify(orderReturnRepository).findReturnSummariesList(
                "%rtn%",
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        );
    }

    @Test
    void getReturnsUsesInMemoryPaginationAfterDateFilter() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);
        List<OrderReturnSummaryResponse> summaries = List.of(
                summary("RET-PAGE-1", ReturnStatus.CREATED, 1L),
                summary("RET-PAGE-2", ReturnStatus.CREATED, 1L),
                summary("RET-PAGE-3", ReturnStatus.CREATED, 1L),
                summary("RET-PAGE-4", ReturnStatus.CREATED, 1L),
                summary("RET-PAGE-5", ReturnStatus.CREATED, 1L)
        );
        when(orderReturnRepository.findReturnSummariesList(
                null,
                null,
                startOfDay(fromDate),
                startOfDay(toDate.plusDays(1))
        )).thenReturn(summaries);

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, fromDate, toDate, null, 1, 2);

        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void getReturnsDefaultsToCreatedAtDescendingSort() {
        when(orderReturnRepository.findReturnSummaries(
                isNull(),
                eq(ReturnStatus.CREATED),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.getReturns(null, ReturnStatus.CREATED, null, null, null, 0, 20);

        verify(orderReturnRepository).findReturnSummaries(
                isNull(),
                eq(ReturnStatus.CREATED),
                isNull(),
                isNull(),
                captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }

    @Test
    void getReturnsHandlesEmptyPage() {
        when(orderReturnRepository.findReturnSummariesList(null, null, null, null))
                .thenReturn(List.of());

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getReturnsPreservesProjectedItemCount() {
        OrderReturnSummaryResponse summary = summary("RET-COUNT", ReturnStatus.CREATED, 3L);
        when(orderReturnRepository.findReturnSummariesList(null, null, null, null))
                .thenReturn(List.of(summary));

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, null, null, null, null, 0, 20);

        assertEquals(3L, result.getContent().getFirst().getItemCount());
    }

    @Test
    void getReturnsUsesSingleSummaryQueryWithoutOrderOrItemLookups() {
        when(orderReturnRepository.findReturnSummariesList(null, null, null, null))
                .thenReturn(List.of());

        service.getReturns(null, null, null, null, "createdAt,desc", 0, 20);

        verify(orderReturnRepository).findReturnSummariesList(null, null, null, null);
        verifyNoInteractions(
                orderRepository,
                orderReturnItemRepository,
                orderStatusHistoryRepository,
                orderCodeGenerator
        );
    }

    private void stubEligibleOrder(BigDecimal returnedQuantity) {
        stubEligibleOrder(returnedQuantity, Instant.now().minusSeconds(60));
    }

    private void stubEligibleOrder(BigDecimal returnedQuantity, Instant completedAt) {
        stubEligibleOrderOnly();
        stubCompletedAt(completedAt);
        stubReturnedQuantity(returnedQuantity);
    }

    private void stubEligibleOrderOnly() {
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        lenient().when(orderItemRepository.findReturnableItemsForUpdate(eq(orderId), any()))
                .thenReturn(order.getItems());
    }

    private void stubCompletedAt(Instant completedAt) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .toStatus(OrderStatus.COMPLETED)
                .changedAt(completedAt)
                .build();
        when(orderStatusHistoryRepository.findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(
                orderId,
                OrderStatus.COMPLETED
        )).thenReturn(Optional.of(history));
    }

    private void stubReturnedQuantity(BigDecimal returnedQuantity) {
        when(orderReturnItemRepository.sumReturnedQuantity(eq(orderItemId), any())).thenReturn(returnedQuantity);
    }

    private OrderReturn orderReturn(UUID returnId, ReturnStatus status) {
        return orderReturn(returnId, status, BigDecimal.ONE);
    }

    private OrderReturn orderReturn(UUID returnId, ReturnStatus status, BigDecimal quantity) {
        OrderReturn value = OrderReturn.builder()
                .returnCode("RET-001")
                .order(order)
                .status(status)
                .build();
        value.setId(returnId);
        value.setIsDeleted(false);
        OrderReturnItem returnItem = OrderReturnItem.builder()
                .orderItem(orderItem)
                .quantity(quantity)
                .reason(ReturnReason.QUALITY_ISSUE)
                .condition(ReturnCondition.OPENED)
                .build();
        returnItem.setId(UUID.randomUUID());
        returnItem.setIsDeleted(false);
        value.addItem(returnItem);
        return value;
    }

    private InventoryClient.CustomerReturnQcSummaryResponse qcSummary(
            UUID returnId,
            String inspectionStatus,
            String qcStatus,
            String returnedQuantity,
            String postedQcQuantity,
            String remainingQcQuantity
    ) {
        return new InventoryClient.CustomerReturnQcSummaryResponse(
                returnId,
                "RET-001",
                "RECEIVED",
                UUID.randomUUID(),
                orderId,
                "ORD-001",
                UUID.randomUUID(),
                inspectionStatus,
                qcStatus,
                List.of(new InventoryClient.CustomerReturnQcLineResponse(
                        UUID.randomUUID(),
                        orderItemId,
                        orderItem.getProductVariantId(),
                        new BigDecimal(returnedQuantity),
                        new BigDecimal(postedQcQuantity),
                        new BigDecimal(remainingQcQuantity),
                        qcStatus
                )),
                Instant.now()
        );
    }

    private CreateOrderReturnRequest request(
            UUID itemId,
            String quantity,
            ReturnReason reason,
            ReturnCondition condition,
            String note
    ) {
        return CreateOrderReturnRequest.builder()
                .items(List.of(item(itemId, quantity, reason, condition, note)))
                .build();
    }

    private CreateOrderReturnItemRequest item(
            UUID itemId,
            String quantity,
            ReturnReason reason,
            ReturnCondition condition,
            String note
    ) {
        return CreateOrderReturnItemRequest.builder()
                .orderItemId(itemId)
                .quantity(new BigDecimal(quantity))
                .reason(reason)
                .condition(condition)
                .note(note)
                .build();
    }

    private Order order(OrderStatus status, PaymentStatus paymentStatus) {
        Order value = Order.builder()
                .orderCode("ORD-001")
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
        value.setId(orderId);
        value.setIsDeleted(false);
        return value;
    }

    private OrderItem orderItem(Order parent, UUID itemId, BigDecimal quantity) {
        OrderItem value = OrderItem.builder()
                .order(parent)
                .productVariantId(UUID.randomUUID())
                .skuSnapshot("SKU-001")
                .productNameSnapshot("Tra sen")
                .variantNameSnapshot("Hop 100g")
                .quantity(quantity)
                .build();
        value.setId(itemId);
        value.setIsDeleted(false);
        return value;
    }

    private void assertStatusFilter(ReturnStatus status) {
        OrderReturnSummaryResponse summary = summary("RET-" + status, status, 1L);
        PageRequest expectedPageRequest = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        if (status == ReturnStatus.RECEIVED) {
            when(orderReturnRepository.findReturnSummariesList(null, null, null, null))
                    .thenReturn(List.of(summary));
        } else {
            when(orderReturnRepository.findReturnSummaries(null, status, null, null, expectedPageRequest))
                    .thenReturn(new PageImpl<>(List.of(summary), expectedPageRequest, 1));
        }

        Page<OrderReturnSummaryResponse> result = service.getReturns(null, status, null, null, null, 0, 20);

        assertEquals(status, result.getContent().getFirst().getStatus());
        if (status == ReturnStatus.RECEIVED) {
            verify(orderReturnRepository).findReturnSummariesList(null, null, null, null);
        } else {
            verify(orderReturnRepository).findReturnSummaries(null, status, null, null, expectedPageRequest);
        }
    }

    private void assertKeywordPattern(String keyword, String expectedPattern) {
        when(orderReturnRepository.findReturnSummariesList(expectedPattern, null, null, null))
                .thenReturn(List.of(summary("RET-SEARCH", ReturnStatus.CREATED, 1L)));

        service.getReturns(keyword, null, null, null, null, 0, 20);

        verify(orderReturnRepository).findReturnSummariesList(expectedPattern, null, null, null);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private OrderReturnSummaryResponse summary(String returnCode, ReturnStatus status, Long itemCount) {
        return OrderReturnSummaryResponse.builder()
                .id(UUID.randomUUID())
                .returnCode(returnCode)
                .orderId(orderId)
                .orderCode("ORD-001")
                .status(status)
                .itemCount(itemCount)
                .customerName("Nguyen Van A")
                .customerPhone("0900000000")
                .build();
    }

    @Test
    void recordPhysicalCompletion_recordsQcCompletedHistory() {
        UUID returnId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        OrderReturn orderReturn = OrderReturn.builder()
                .status(ReturnStatus.RECEIVED)
                .build();
        orderReturn.setId(returnId);

        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId))
                .thenReturn(Optional.of(orderReturn));

        service.recordPhysicalCompletion(returnId, actorUserId, inspectionId);

        verify(orderReturnHistoryWriter).writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.QC_COMPLETED,
                ReturnStatus.RECEIVED,
                ReturnStatus.RECEIVED,
                actorUserId,
                "Inventory physical disposition completed",
                "INVENTORY_INSPECTION",
                inspectionId.toString()
        );
    }

    @Test
    void recordPhysicalCompletion_whenReturnNotFound_doesNothing() {
        UUID returnId = UUID.randomUUID();
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId))
                .thenReturn(Optional.empty());

        service.recordPhysicalCompletion(returnId, null, null);

        verify(orderReturnHistoryWriter, never()).writeIfAbsent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitReturnInventoryDisposition_quarantineWithoutDestination_throwsException() {
        UUID returnId = UUID.randomUUID();
        UUID returnItemId = UUID.randomUUID();
        OrderReturn orderReturn = OrderReturn.builder()
                .status(ReturnStatus.RECEIVED)
                .build();
        orderReturn.setId(returnId);

        OrderReturnItem item = OrderReturnItem.builder()
                .quantity(new BigDecimal("2.000"))
                .build();
        item.setId(returnItemId);
        item.setIsDeleted(false);
        orderReturn.setItems(List.of(item));

        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId))
                .thenReturn(Optional.of(orderReturn));

        SubmitReturnInventoryDispositionRequest request = SubmitReturnInventoryDispositionRequest.builder()
                .commandId(UUID.randomUUID())
                .lines(List.of(
                        SubmitReturnInventoryDispositionLineRequest.builder()
                                .returnItemId(returnItemId)
                                .quantity(new BigDecimal("1.000"))
                                .result(ReturnInventoryDispositionResult.QUARANTINE)
                                .destinationLocationId(null)
                                .build()
                ))
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                InvalidOrderReturnException.class,
                () -> service.submitInventoryDisposition(returnId, request)
        );
    }

    @Test
    void submitReturnInventoryDisposition_quarantineWithDestination_succeeds() {
        UUID returnId = UUID.randomUUID();
        UUID returnItemId = UUID.randomUUID();
        UUID locId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();

        OrderReturn orderReturn = OrderReturn.builder()
                .status(ReturnStatus.RECEIVED)
                .build();
        orderReturn.setId(returnId);

        OrderItem ordItem = OrderItem.builder()
                .productVariantId(UUID.randomUUID())
                .build();
        ordItem.setId(UUID.randomUUID());

        OrderReturnItem item = OrderReturnItem.builder()
                .quantity(new BigDecimal("2.000"))
                .orderItem(ordItem)
                .build();
        item.setId(returnItemId);
        item.setIsDeleted(false);
        orderReturn.setItems(List.of(item));

        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId))
                .thenReturn(Optional.of(orderReturn));
        when(orderReturnInventoryCommandRepository.findByCommandIdAndIsDeletedFalse(cmdId))
                .thenReturn(Optional.empty());
        when(orderReturnInventoryCommandRepository.sumCommandedQuantityByReturnItemId(returnItemId))
                .thenReturn(BigDecimal.ZERO);
        when(orderReturnInventoryCommandRepository.saveAndFlush(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        SubmitReturnInventoryDispositionRequest request = SubmitReturnInventoryDispositionRequest.builder()
                .commandId(cmdId)
                .lines(List.of(
                        SubmitReturnInventoryDispositionLineRequest.builder()
                                .returnItemId(returnItemId)
                                .quantity(new BigDecimal("1.000"))
                                .result(ReturnInventoryDispositionResult.QUARANTINE)
                                .destinationLocationId(locId)
                                .build()
                ))
                .build();

        var response = service.submitInventoryDisposition(returnId, request);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals(cmdId, response.getCommandId());
        verify(orderReturnInventoryCommandRepository).saveAndFlush(any());
    }
}
