package org.hvtra.orderservice.returnrefund;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommandLine;
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
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.impl.OrderReturnRefundTransactionServiceImpl;
import org.hvtra.orderservice.service.impl.OrderReturnServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ReturnRefundTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
                "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "spring.sql.init.mode=never",
                "storage.initialization.enabled=false", "spring.flyway.enabled=true",
                "spring.flyway.clean-disabled=true", "spring.flyway.create-schemas=false",
                "spring.flyway.baseline-on-migrate=false", "spring.flyway.out-of-order=false",
                "spring.flyway.locations=classpath:db/migration", "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false", "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false", "kafka-config.enabled=false",
                "logging.level.org.hibernate.SQL=OFF", "spring.main.banner-mode=off"})
@Timeout(45)
class ReturnRefundMySqlIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        IsolatedMySql.start();
        registry.add("spring.datasource.url", IsolatedMySql.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", IsolatedMySql.MYSQL::getUsername);
        registry.add("spring.datasource.password", IsolatedMySql.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", IsolatedMySql.MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", IsolatedMySql.MYSQL::getUsername);
        registry.add("spring.flyway.password", IsolatedMySql.MYSQL::getPassword);
        registry.add("spring.flyway.schemas", () -> IsolatedMySql.DATABASE);
        registry.add("spring.flyway.default-schema", () -> IsolatedMySql.DATABASE);
    }

    @Autowired OrderReturnServiceImpl service;
    @Autowired OrderReturnRefundTransactionServiceImpl refundWriter;
    @Autowired OrderRepository orders;
    @Autowired OrderReturnRepository returns;
    @Autowired OrderReturnItemRepository items;
    @Autowired PaymentClient payment;
    @Autowired InventoryClient inventory;
    @Autowired TestBoundaryProbe probe;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @PersistenceContext EntityManager entityManager;
    private UUID orderId;
    private UUID itemId;
    private final Map<UUID, PaymentClient.PaymentRefundResponse> remoteRefunds = new ConcurrentHashMap<>();
    private final UUID paymentId = UUID.randomUUID();
    private BigDecimal paid = new BigDecimal("500");

    @BeforeEach
    void fixture() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            IsolatedMySql.verify(connection);
        }
        reset(payment, inventory);
        probe.reset();
        remoteRefunds.clear();
        paid = new BigDecimal("500");
        tx(() -> {
            Order order = Order.builder().orderCode("IT-" + UUID.randomUUID()).orderType("POS")
                    .status(OrderStatus.COMPLETED).paymentStatus(PaymentStatus.PAID)
                    .returnStatus(OrderReturnStatus.NONE).originalAmount(paid).discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO).finalAmount(paid).paidAmount(paid).debtAmount(BigDecimal.ZERO).build();
            OrderItem item = OrderItem.builder().productVariantId(UUID.randomUUID())
                    .productNameSnapshot("Isolated integration fixture").quantity(new BigDecimal("5"))
                    .originalUnitPrice(new BigDecimal("100")).actualUnitPrice(new BigDecimal("100"))
                    .discountAmount(BigDecimal.ZERO).lineTotal(paid).build();
            order.addItem(item);
            entityManager.persist(order);
            entityManager.flush();
            orderId = order.getId();
            itemId = item.getId();
            return null;
        });
        when(payment.lookupRefundSummaries(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Payment lookup must be outside local transaction");
            PaymentClient.RefundSummaryLookupRequest request = call.getArgument(0);
            return request.items().stream().map(key -> summary(key.returnId())).toList();
        });
        when(payment.refundOrderReturn(eq(orderId), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Payment refund must not hold DB transaction");
            PaymentClient.PaymentRefundRequest request = call.getArgument(1);
            return remoteRefunds.computeIfAbsent(request.returnId(), id -> response(id, request.amount()));
        });
    }

    @AfterAll static void stopContainer() { IsolatedMySql.MYSQL.stop(); }

    @Test void R1_partialReturnSurvivesNewPersistenceContext() {
        UUID id = create("2");
        tx(() -> {
            entityManager.clear();
            OrderReturn record = returns.findById(id).orElseThrow();
            assertEquals(orderId, record.getOrder().getId());
            assertEquals(itemId, record.getItems().getFirst().getOrderItem().getId());
            assertMoney("2", record.getItems().getFirst().getQuantity());
            assertMoney("3", remaining());
            return null;
        });
        evidence("R1");
    }

    @Test void R2_fullReturnPreventsAnotherReturn() {
        create("5");
        assertMoney("0", remaining());
        assertThrows(InvalidOrderReturnException.class, () -> create("1"));
        assertMoney("5", returned());
        evidence("R2");
    }

    @Test void R3_repeatedPartialReturnsReserveExactlyPurchasedQuantity() {
        create("2"); create("2"); create("1");
        assertMoney("0", remaining());
        assertThrows(InvalidOrderReturnException.class, () -> create("1"));
        assertEquals(3, returnCount());
        evidence("R3");
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"-1", "0", "6"})
    void R4_invalidQuantityLeavesNoPartialWrites(String quantity) {
        long outbox = count("outbox_events");
        assertThrows(InvalidOrderReturnException.class,
                () -> service.createReturn(orderId, request(quantity == null ? null : new BigDecimal(quantity))));
        assertEquals(0, returnCount());
        assertEquals(outbox, count("outbox_events"));
        assertMoney("0", returned());
        assertEquals(PaymentStatus.PAID, orders.findById(orderId).orElseThrow().getPaymentStatus());
    }

    @Test void R4_remainingQuantityAndFractionalContract() {
        create("4.999");
        assertThrows(InvalidOrderReturnException.class, () -> create("0.002"));
        create("0.001");
        assertMoney("0", remaining());
        evidence("R4-fractional");
    }

    @Test void R5_realFlushedReturnHistoryAndOutboxRollBackTogether() {
        long outbox = count("outbox_events");
        long history = count("order_return_history");
        probe.failAfterNotification = "publishReturnCreatedNotification";
        assertThrows(TestBoundaryProbe.InjectedFailure.class, () -> create("2"));
        assertFalse(probe.events.isEmpty(), "Failure must happen after actual flush");
        assertEquals(0, returnCount());
        assertMoney("0", returned());
        assertEquals(outbox, count("outbox_events"));
        assertEquals(history, count("order_return_history"));
        IsolatedMySql.evidence("R5-flush-and-rollback", Map.of("probe", probe.events, "committedReturns", returnCount()));
    }

    @Test void R6_concurrentReturnsObserveMySqlLockAndRejectOverReturn() throws Exception {
        create("3");
        concurrentReturns("2", "2", 1);
        assertMoney("5", returned());
        assertMoney("0", remaining());
        evidence("R6");
    }

    @Test void R7_concurrentDifferentQuantitiesDoNotLoseUpdates() throws Exception {
        create("2");
        concurrentReturns("1", "2", 2);
        assertMoney("5", returned());
        assertEquals(3, returnCount());
        evidence("R7");
    }

    @Test void F1_refundPersistsCompletionAndPaymentReference() {
        UUID id = ready("2");
        OrderReturnRefundResponse response = refund(id);
        assertMoney("200", response.getRefundAmount());
        assertEquals(remoteRefunds.get(id).refundTransactionId(), response.getRefundTransactionId());
        assertEquals(ReturnStatus.COMPLETED, returns.findById(id).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, orders.findById(orderId).orElseThrow().getPaymentStatus());
        verify(payment).refundOrderReturn(orderId, new PaymentClient.PaymentRefundRequest(id, new BigDecimal("200")));
        assertEquals(1, refundHistory(id));
        assertEquals(response.getRefundTransactionId().toString(), jdbc.queryForObject(
                "SELECT reference_id FROM order_return_history WHERE return_id=UUID_TO_BIN(?) AND action='REFUND_COMPLETED'",
                String.class, id.toString()));
        evidence("F1");
    }

    @Test void F2_unreceivedReturnCannotRefund() {
        UUID id = create("2");
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        verify(payment, never()).refundOrderReturn(any(), any());
        assertEquals(ReturnStatus.CREATED, returns.findById(id).orElseThrow().getStatus());
    }

    @Test void F2_receivedWithoutProcessedPhysicalCommandCannotRefund() {
        UUID id = create("2"); service.receiveReturn(id);
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        verify(payment, never()).refundOrderReturn(any(), any());
        assertEquals(0, refundHistory(id));
    }

    @ParameterizedTest @ValueSource(strings = {"UNPAID", "REFUNDED"})
    void F2_paymentNotEligibleIsRejectedBeforeDispatch(String status) {
        UUID id = ready("2");
        when(payment.lookupRefundSummaries(any())).thenReturn(List.of(new PaymentClient.PaymentRefundSummaryResponse(
                id, orderId, paymentId, null, null, BigDecimal.ZERO, paid, "NOT_REFUNDED", status, "CASH", null)));
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        verify(payment, never()).refundOrderReturn(any(), any());
        assertEquals(ReturnStatus.RECEIVED, returns.findById(id).orElseThrow().getStatus());
    }

    @Test void F2_zeroCalculatedAmountAndExcessRemainingAreBlocked() {
        UUID id = ready("2");
        tx(() -> { orders.findById(orderId).orElseThrow().getItems().getFirst().setLineTotal(BigDecimal.ZERO); return null; });
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        tx(() -> { orders.findById(orderId).orElseThrow().getItems().getFirst().setLineTotal(paid); return null; });
        when(payment.lookupRefundSummaries(any())).thenReturn(List.of(new PaymentClient.PaymentRefundSummaryResponse(
                id, orderId, paymentId, null, null, new BigDecimal("450"), paid,
                "NOT_REFUNDED", "PARTIALLY_REFUNDED", "CASH", null)));
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        verify(payment, never()).refundOrderReturn(any(), any());
    }

    @Test void F3_E1_multipleReturnsRefundSeparatelyWithRoundingAndNoOverRefund() {
        paid = new BigDecimal("101");
        tx(() -> {
            Order order = orders.findById(orderId).orElseThrow();
            order.setOriginalAmount(paid); order.setFinalAmount(paid); order.setPaidAmount(paid);
            order.getItems().getFirst().setLineTotal(paid);
            return null;
        });
        List<BigDecimal> amounts = new ArrayList<>();
        for (String quantity : List.of("2", "2", "1")) amounts.add(refund(ready(quantity)).getRefundAmount());
        assertEquals(List.of(new BigDecimal("40"), new BigDecimal("41"), new BigDecimal("20")), amounts);
        assertMoney("101", totalRefunded());
        assertMoney("0", remaining());
        assertEquals(3, remoteRefunds.size());
        assertEquals(PaymentStatus.REFUNDED, orders.findById(orderId).orElseThrow().getPaymentStatus());
        evidence("F3-E1");
    }

    @Test void F4_replayUsesExistingPaymentIdentityWithoutSecondRefundDispatch() {
        UUID id = ready("5");
        OrderReturnRefundResponse first = refund(id);
        long outbox = count("outbox_events");
        OrderReturnRefundResponse replay = refund(id);
        assertEquals(first.getRefundTransactionId(), replay.getRefundTransactionId());
        assertMoney("500", totalRefunded());
        assertEquals(1, refundHistory(id));
        assertEquals(outbox, count("outbox_events"));
        verify(payment, times(1)).refundOrderReturn(any(), any());
        evidence("F4");
    }

    @Test void F5_concurrentRefundsConvergeLocallyButDispatchTwiceToIdempotentPaymentMock() throws Exception {
        UUID id = ready("5");
        CyclicBarrier externalCalls = new CyclicBarrier(2);
        AtomicInteger calls = new AtomicInteger();
        when(payment.refundOrderReturn(eq(orderId), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            calls.incrementAndGet();
            externalCalls.await(10, TimeUnit.SECONDS);
            PaymentClient.PaymentRefundRequest request = call.getArgument(1);
            return remoteRefunds.computeIfAbsent(id, key -> response(key, request.amount()));
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> refund(id));
            var second = executor.submit(() -> refund(id));
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.getRefundTransactionId(), b.getRefundTransactionId());
            assertEquals(2, calls.get(), "Characterization: Order does not deduplicate in-flight Payment calls");
            assertEquals(1, refundHistory(id));
            assertMoney("500", totalRefunded());
            IsolatedMySql.evidence("F5-concurrent-refund", Map.of("overlapAtPaymentBarrier", true,
                    "paymentDispatches", calls.get(), "distinctMockLedgerRecords", remoteRefunds.size(),
                    "localRefundHistory", refundHistory(id), "remoteIdempotencyIsMocked", true));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        evidence("F5");
    }

    @ParameterizedTest @ValueSource(ints = {400, 409, 500, 503})
    void F6_paymentHttpFailureLeavesLocalReturnUnrefunded(int status) {
        UUID id = ready("2");
        Request request = Request.create(Request.HttpMethod.POST, "http://mock-payment/refund", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        FeignException failure = FeignException.errorStatus("refund", Response.builder().request(request)
                .status(status).reason("controlled failure").headers(Map.of()).build());
        when(payment.refundOrderReturn(eq(orderId), any())).thenThrow(failure);
        assertSame(failure, assertThrows(FeignException.class, () -> refund(id)));
        assertEquals(ReturnStatus.RECEIVED, returns.findById(id).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PAID, orders.findById(orderId).orElseThrow().getPaymentStatus());
        assertEquals(0, refundHistory(id));
        assertTrue(remoteRefunds.isEmpty());
    }

    @Test void F6_unknownExternalOutcomeCanReplayAfterPaymentLookupConfirmsSuccess() {
        UUID id = ready("5");
        when(payment.refundOrderReturn(eq(orderId), any())).thenAnswer(call -> {
            remoteRefunds.put(id, response(id, paid));
            throw new IllegalStateException("Mock timeout after external success; outcome unknown to caller");
        });
        assertThrows(IllegalStateException.class, () -> refund(id));
        assertEquals(ReturnStatus.RECEIVED, returns.findById(id).orElseThrow().getStatus());
        refund(id);
        verify(payment, times(1)).refundOrderReturn(any(), any());
        assertEquals(ReturnStatus.COMPLETED, returns.findById(id).orElseThrow().getStatus());
        evidence("F6-unknown-outcome");
    }

    @ParameterizedTest @ValueSource(strings = {"timeout", "connection failure"})
    void F6_transportFailureDoesNotRecordAnUnconfirmedRefund(String reason) {
        UUID id = ready("2");
        Request request = Request.create(Request.HttpMethod.POST, "http://mock-payment/refund", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        RetryableException failure = new RetryableException(-1, reason, Request.HttpMethod.POST, (Long) null, request);
        when(payment.refundOrderReturn(eq(orderId), any())).thenThrow(failure);
        assertSame(failure, assertThrows(RetryableException.class, () -> refund(id)));
        assertEquals(ReturnStatus.RECEIVED, returns.findById(id).orElseThrow().getStatus());
        assertEquals(0, refundHistory(id));
        assertTrue(remoteRefunds.isEmpty());
        verify(payment, times(1)).refundOrderReturn(any(), any());
    }

    @Test void F7_externalSuccessIsNotUndoneByLocalRollbackAndReplayReconciles() {
        UUID id = ready("5");
        long outbox = count("outbox_events");
        probe.failAfterNotification = "publishReturnRefundCompletedNotification";
        assertThrows(TestBoundaryProbe.InjectedFailure.class, () -> refund(id));
        assertEquals(1, remoteRefunds.size(), "MySQL rollback cannot undo external success");
        assertEquals(ReturnStatus.RECEIVED, returns.findById(id).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PAID, orders.findById(orderId).orElseThrow().getPaymentStatus());
        assertEquals(0, refundHistory(id));
        assertEquals(outbox, count("outbox_events"));
        IsolatedMySql.evidence("F7-rollback", Map.of("probe", probe.events, "mockPaymentSuccessRetained", true));
        probe.failAfterNotification = null;
        refund(id);
        verify(payment, times(1)).refundOrderReturn(any(), any());
        assertEquals(1, refundHistory(id));
        evidence("F7");
    }

    @Test void F8_latePartialResultCannotDowngradeFullRefundAndDuplicateIsIdempotent() {
        UUID partialId = ready("2");
        var partial = refund(partialId);
        refund(ready("3"));
        var remote = remoteRefunds.get(partialId);
        refundWriter.applyRefundSuccess(partialId, remote, null);
        refundWriter.applyRefundSuccess(partialId, remote, null);
        assertEquals(PaymentStatus.REFUNDED, orders.findById(orderId).orElseThrow().getPaymentStatus());
        assertEquals(1, refundHistory(partialId));
        assertMoney("200", partial.getRefundAmount());
        evidence("F8");
    }

    @Test void E2_cancelledReturnReleasesQuantityButCannotRefund() {
        UUID id = create("5"); service.cancelReturn(id);
        assertMoney("5", remaining());
        assertThrows(InvalidOrderReturnException.class, () -> refund(id));
        create("5");
        assertMoney("0", remaining());
        verify(payment, never()).refundOrderReturn(any(), any());
        evidence("E2");
    }

    @Test void E3_databaseUniqueHistoryConstraintRejectsDuplicateIdentity() {
        UUID id = ready("5"); refund(id);
        ConstraintViolationException duplicate = assertThrows(ConstraintViolationException.class, () -> tx(() -> {
            entityManager.createNativeQuery("INSERT INTO order_return_history "
                    + "(id, return_id, action, occurred_at, is_deleted) "
                    + "SELECT UUID_TO_BIN(UUID()), return_id, action, occurred_at, 0 FROM order_return_history "
                    + "WHERE return_id = :id AND action='REFUND_COMPLETED'")
                    .setParameter("id", id).executeUpdate();
            return null;
        }));
        assertEquals(1062, duplicate.getSQLException().getErrorCode());
        assertEquals("23000", duplicate.getSQLException().getSQLState());
        assertEquals(1, refundHistory(id));
        evidence("E3");
    }

    private void concurrentReturns(String a, String b, int expectedSuccess) throws Exception {
        probe.holdOrderLock = true;
        CyclicBarrier start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> returnAttempt(a, start));
            var second = executor.submit(() -> returnAttempt(b, start));
            assertTrue(probe.locked.await(10, TimeUnit.SECONDS), "First transaction must hold real order lock");
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50))
                    .until(() -> IsolatedMySql.lockWaits() > 0);
            long waits = IsolatedMySql.lockWaits();
            probe.release.countDown();
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(expectedSuccess, outcomes.stream().filter("COMMITTED"::equals).count());
            var before = probe.events.stream().filter(e -> "before-order-lock".equals(e.get("point"))).toList();
            assertEquals(2, before.stream().map(e -> e.get("connectionId")).distinct().count());
            assertEquals(2, before.stream().map(e -> e.get("entityManagerIdentity")).distinct().count());
            IsolatedMySql.evidence("return-concurrency-" + a + "-" + b,
                    Map.of("lockWaitRowsObserved", waits, "events", probe.events, "outcomes", outcomes));
        } finally {
            probe.release.countDown();
            probe.holdOrderLock = false;
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private String returnAttempt(String quantity, CyclicBarrier start) {
        try {
            return tx(() -> {
                try { start.await(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException("Start barrier failed", exception); }
                create(quantity);
                return "COMMITTED";
            });
        } catch (InvalidOrderReturnException businessRejection) {
            assertTrue(businessRejection.getMessage().contains("quantity")
                    || businessRejection.getMessage().contains("Quantity"), businessRejection.getMessage());
            return "BUSINESS_REJECTED: " + businessRejection.getMessage();
        }
    }

    private UUID create(String quantity) { return service.createReturn(orderId, request(new BigDecimal(quantity))).getId(); }
    private CreateOrderReturnRequest request(BigDecimal quantity) {
        return CreateOrderReturnRequest.builder().refundRequired(true).items(List.of(CreateOrderReturnItemRequest.builder()
                .orderItemId(itemId).quantity(quantity).reason(ReturnReason.DEFECTIVE).condition(ReturnCondition.SEALED).build())).build();
    }
    private UUID ready(String quantity) {
        UUID id = create(quantity); service.receiveReturn(id);
        tx(() -> {
            OrderReturn record = returns.findById(id).orElseThrow();
            var line = record.getItems().getFirst();
            var command = OrderReturnInventoryCommand.builder().orderReturn(record).commandId(UUID.randomUUID())
                    .status(OrderReturnInventoryCommandStatus.PROCESSED).inspectionId(UUID.randomUUID()).build();
            command.addLine(OrderReturnInventoryCommandLine.builder().returnItem(line).orderItemId(itemId)
                    .productVariantId(line.getOrderItem().getProductVariantId()).quantity(line.getQuantity())
                    .result(ReturnInventoryDispositionResult.WRITE_OFF).build());
            entityManager.persist(command);
            return null;
        });
        return id;
    }
    /** Mirrors Spring's request-scoped OSIV persistence context without wrapping Payment in a transaction. */
    private OrderReturnRefundResponse refund(UUID id) {
        EntityManager requestEntityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(requestEntityManager));
        try { return service.refundReturn(id); }
        finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestEntityManager.close();
        }
    }
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactionManager).execute(status -> action.get()); }
    private BigDecimal returned() {
        BigDecimal total = items.sumReturnedQuantity(itemId, List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED));
        return total == null ? BigDecimal.ZERO : total;
    }
    private BigDecimal remaining() { return new BigDecimal("5").subtract(returned()); }
    private long returnCount() { return tx(() -> (long) returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId).size()); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private long refundHistory(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_return_history WHERE return_id=UUID_TO_BIN(?) AND action='REFUND_COMPLETED'", Long.class, id.toString());
    }
    private BigDecimal totalRefunded() {
        return remoteRefunds.values().stream().map(PaymentClient.PaymentRefundResponse::refundAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private PaymentClient.PaymentRefundResponse response(UUID id, BigDecimal amount) {
        BigDecimal total = totalRefunded().add(amount);
        return new PaymentClient.PaymentRefundResponse(paymentId, orderId, UUID.randomUUID(), id, amount, total,
                total.compareTo(paid) >= 0 ? "REFUNDED" : "PARTIALLY_REFUNDED", "CASH", LocalDateTime.of(2026, 11, 4, 0, 0));
    }
    private PaymentClient.PaymentRefundSummaryResponse summary(UUID id) {
        var refund = remoteRefunds.get(id);
        return new PaymentClient.PaymentRefundSummaryResponse(id, orderId, paymentId,
                refund == null ? null : refund.refundTransactionId(), refund == null ? null : refund.refundAmount(),
                totalRefunded(), paid, refund == null ? "NOT_REFUNDED" : "REFUNDED",
                totalRefunded().signum() == 0 ? "PAID" : totalRefunded().compareTo(paid) >= 0 ? "REFUNDED" : "PARTIALLY_REFUNDED",
                "CASH", refund == null ? null : refund.refundedAt());
    }
    private void evidence(String scenario) {
        tx(() -> {
            entityManager.clear();
            var order = orders.findById(orderId).orElseThrow();
            List<Map<String, Object>> rows = returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId).stream()
                    .map(r -> Map.<String, Object>of("returnId", r.getId(), "status", r.getStatus(),
                            "quantity", r.getItems().getFirst().getQuantity(), "refundHistoryCount", refundHistory(r.getId())))
                    .toList();
            IsolatedMySql.evidence(scenario, Map.of("orderId", orderId, "itemId", itemId, "returns", rows,
                    "returnedQuantity", returned(), "remainingQuantity", remaining(), "paymentStatus", order.getPaymentStatus(),
                    "orderReturnStatus", order.getReturnStatus(), "mockRefundTotal", totalRefunded()));
            return null;
        });
    }
    private static void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
