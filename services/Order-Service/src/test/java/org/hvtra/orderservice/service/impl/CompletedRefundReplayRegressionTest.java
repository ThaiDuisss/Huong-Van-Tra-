package org.hvtra.orderservice.service.impl;

import jakarta.persistence.EntityManager;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.controller.OrderController;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.exception.OrderGlobalExceptionHandler;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderItemRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnRefundTransactionService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnCreationEligibilityResolver;
import org.hvtra.orderservice.service.support.OrderReturnDisplayStatusResolver;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgressResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:refund_replay_regression;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "storage.initialization.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.hibernate.SQL=OFF"
})
@ContextConfiguration(classes = CompletedRefundReplayRegressionTest.JpaConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompletedRefundReplayRegressionTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Order.class)
    @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
    @Import(OrderReturnRefundTransactionServiceImpl.class)
    static class JpaConfiguration {
        @Bean PaymentClient paymentClient() { return mock(PaymentClient.class); }
        @Bean OrderReturnHistoryWriter historyWriter() { return mock(OrderReturnHistoryWriter.class); }
        @Bean OrderOutboxPublisher outboxPublisher() { return mock(OrderOutboxPublisher.class); }
        @Bean OrderReturnPhysicalCompletionHandler physicalCompletionHandler() {
            return mock(OrderReturnPhysicalCompletionHandler.class);
        }
        @Bean OrderReturnServiceImpl returnService(
                OrderRepository orders, OrderReturnRepository returns, PaymentClient payment,
                OrderReturnRefundTransactionService writer, OrderReturnHistoryWriter history,
                OrderOutboxPublisher outbox, OrderReturnPhysicalCompletionHandler physical) {
            return new OrderReturnServiceImpl(
                    orders, mock(OrderItemRepository.class), returns, mock(OrderReturnItemRepository.class),
                    mock(OrderReturnInventoryCommandRepository.class), mock(OrderReturnHistoryRepository.class),
                    mock(OrderCodeGenerator.class), new OrderReturnMapper(), mock(InventoryClient.class), payment,
                    mock(ReturnRefundCalculator.class), history, outbox, writer,
                    mock(OrderReturnInventoryCommandReconciliationWriter.class),
                    mock(OrderReturnPhysicalProgressResolver.class), new OrderReturnDisplayStatusResolver(),
                    new OrderReturnRefundEligibilityResolver(), physical, new OrderReturnCreationEligibilityResolver());
        }
    }

    @Autowired OrderReturnServiceImpl service;
    @Autowired OrderRepository orders;
    @Autowired OrderReturnRepository returns;
    @Autowired PaymentClient payment;
    @Autowired OrderReturnHistoryWriter history;
    @Autowired OrderOutboxPublisher outbox;
    @Autowired OrderReturnPhysicalCompletionHandler physical;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    private MockMvc mvc;
    private UUID orderId;
    private UUID returnId;
    private UUID refundId;
    private PaymentClient.PaymentRefundSummaryResponse summary;

    @BeforeEach
    void setUp() {
        reset(payment, history, outbox, physical);
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class),
                        mock(PosOfflineCheckoutService.class), mock(OrderExportService.class), service,
                        mock(OrderReturnReportService.class)))
                .setControllerAdvice(new OrderGlobalExceptionHandler()).build();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            BigDecimal amount = new BigDecimal("10000");
            Order order = Order.builder().orderCode("D2-" + UUID.randomUUID()).orderType("POS")
                    .status(OrderStatus.COMPLETED).paymentStatus(PaymentStatus.REFUNDED)
                    .returnStatus(OrderReturnStatus.FULLY_RETURNED)
                    .originalAmount(amount).discountAmount(BigDecimal.ZERO).shippingFee(BigDecimal.ZERO)
                    .finalAmount(amount).paidAmount(amount).debtAmount(BigDecimal.ZERO).build();
            OrderItem item = OrderItem.builder().productVariantId(UUID.randomUUID()).productNameSnapshot("D2 regression")
                    .quantity(BigDecimal.ONE).originalUnitPrice(amount).actualUnitPrice(amount)
                    .discountAmount(BigDecimal.ZERO).lineTotal(amount).build();
            order.addItem(item);
            entityManager.persist(order);
            entityManager.flush();
            OrderReturn record = OrderReturn.builder().order(order).returnCode("D2-" + UUID.randomUUID())
                    .status(ReturnStatus.COMPLETED).refundRequired(true).build();
            record.addItem(OrderReturnItem.builder().orderItem(item).quantity(BigDecimal.ONE)
                    .reason(ReturnReason.DEFECTIVE).condition(ReturnCondition.SEALED).build());
            entityManager.persist(record);
            entityManager.flush();
            orderId = order.getId();
            returnId = record.getId();
        });
        refundId = UUID.randomUUID();
        summary = summary(refundId);
        when(payment.lookupRefundSummaries(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Payment lookup must not hold DB locks");
            return List.of(summary);
        });
    }

    @Test
    void completedRefundReplaysWithHttp200AndExistingTransactionWithoutAnotherRefund() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refundTransactionId").value(refundId.toString()))
                    .andExpect(jsonPath("$.refundAmount").value(10000))
                    .andExpect(jsonPath("$.totalAmountRefunded").value(10000))
                    .andExpect(jsonPath("$.returnStatus").value("COMPLETED"));
        }
        assertEquals(PaymentStatus.REFUNDED, orders.findById(orderId).orElseThrow().getPaymentStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(history, outbox);
    }

    @Test
    void concurrentCompletedReplaysReturnSameLedgerReference() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return service.refundReturn(returnId); });
            var second = executor.submit(() -> { start.await(); return service.refundReturn(returnId); });
            start.countDown();
            assertEquals(refundId, first.get(15, TimeUnit.SECONDS).getRefundTransactionId());
            assertEquals(refundId, second.get(15, TimeUnit.SECONDS).getRefundTransactionId());
        } finally {
            executor.shutdownNow();
        }
        verify(payment, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(history, outbox);
    }

    @Test
    void fullyRefundedPaymentWithoutThisReturnsTransactionIsControlled4xx() throws Exception {
        summary = summary(null);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(payment, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(history, outbox);
    }

    @Test
    void paymentSuccessCanReconcilePreviouslyFailedOrderWriteAfterPhysicalCompletion() throws Exception {
        setReturnStatus(ReturnStatus.RECEIVED);
        when(physical.isPhysicalDispositionCompleted(returnId)).thenReturn(true);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTransactionId").value(refundId.toString()))
                .andExpect(jsonPath("$.returnStatus").value("COMPLETED"));
        assertEquals(ReturnStatus.COMPLETED, returns.findById(returnId).orElseThrow().getStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
    }

    @Test
    void replayCannotCompleteReceivedReturnWithPendingPhysicalDisposition() throws Exception {
        setReturnStatus(ReturnStatus.RECEIVED);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(ReturnStatus.RECEIVED, returns.findById(returnId).orElseThrow().getStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(history, outbox);
    }

    @Test
    void partialPaymentSuccessReconcilesFailedOrderWriteAndKeepsCompletedPartialReplay4xx() throws Exception {
        setReturnStatus(ReturnStatus.RECEIVED);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Order order = orders.findById(orderId).orElseThrow();
            BigDecimal amount = new BigDecimal("20000");
            order.setPaidAmount(amount);
            order.setOriginalAmount(amount);
            order.setFinalAmount(amount);
            order.getItems().getFirst().setQuantity(new BigDecimal("2"));
            order.getItems().getFirst().setLineTotal(amount);
        });
        summary = new PaymentClient.PaymentRefundSummaryResponse(returnId, orderId, UUID.randomUUID(), refundId,
                new BigDecimal("10000"), new BigDecimal("10000"), new BigDecimal("20000"),
                "PARTIALLY_REFUNDED", "PARTIALLY_REFUNDED", "CASH", null);
        when(physical.isPhysicalDispositionCompleted(returnId)).thenReturn(true);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTransactionId").value(refundId.toString()))
                .andExpect(jsonPath("$.totalAmountRefunded").value(10000))
                .andExpect(jsonPath("$.orderPaymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.orderReturnStatus").value("PARTIALLY_RETURNED"));
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(ReturnStatus.COMPLETED, returns.findById(returnId).orElseThrow().getStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
    }

    @Test
    void failedLocalWriteRollsBackAndNextReplayReconcilesSamePaymentTransaction() throws Exception {
        setReturnStatus(ReturnStatus.RECEIVED);
        when(physical.isPhysicalDispositionCompleted(returnId)).thenReturn(true);
        doThrow(new IllegalStateException("Simulated local write failure")).doNothing()
                .when(history).writeIfAbsent(any(), any(), any(), any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.refundReturn(returnId));
        assertEquals(ReturnStatus.RECEIVED, returns.findById(returnId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PAID, orders.findById(orderId).orElseThrow().getPaymentStatus());
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTransactionId").value(refundId.toString()));
        assertEquals(ReturnStatus.COMPLETED, returns.findById(returnId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, orders.findById(orderId).orElseThrow().getPaymentStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
    }

    @Test
    void replayCannotCompleteCancelledReturn() throws Exception {
        setReturnStatus(ReturnStatus.CANCELLED);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(ReturnStatus.CANCELLED, returns.findById(returnId).orElseThrow().getStatus());
        verify(payment, never()).refundOrderReturn(any(), any());
    }

    private void setReturnStatus(ReturnStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            OrderReturn record = returns.findById(returnId).orElseThrow();
            record.setStatus(status);
            returns.save(record);
            if (status == ReturnStatus.RECEIVED) {
                Order order = orders.findById(orderId).orElseThrow();
                order.setPaymentStatus(PaymentStatus.PAID);
                orders.save(order);
            }
        });
    }

    private PaymentClient.PaymentRefundSummaryResponse summary(UUID transactionId) {
        return new PaymentClient.PaymentRefundSummaryResponse(returnId, orderId, UUID.randomUUID(), transactionId,
                new BigDecimal("10000"), new BigDecimal("10000"), new BigDecimal("10000"),
                "REFUNDED", "REFUNDED", "CASH", null);
    }

    private String path() { return "/api/orders/returns/" + returnId + "/refund"; }
}
