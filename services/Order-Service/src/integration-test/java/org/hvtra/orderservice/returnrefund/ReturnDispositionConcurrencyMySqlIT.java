package org.hvtra.orderservice.returnrefund;

import jakarta.persistence.EntityManager;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionLineRequest;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.impl.OrderReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Domain concurrency verification; does not certify the historical Flyway chain. */
@SpringBootTest(classes = ReturnRefundTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "spring.sql.init.mode=never",
                "storage.initialization.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false", "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
                "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false",
                "kafka-config.enabled=false", "logging.level.org.hibernate.SQL=OFF"})
@Timeout(45)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReturnDispositionConcurrencyMySqlIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        IsolatedMySql.start();
        registry.add("spring.datasource.url", IsolatedMySql.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", IsolatedMySql.MYSQL::getUsername);
        registry.add("spring.datasource.password", IsolatedMySql.MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired OrderReturnServiceImpl service;
    @Autowired OrderReturnRepository returns;
    @Autowired OrderReturnInventoryCommandRepository commands;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    private UUID orderId;
    private UUID itemId;

    @BeforeEach
    void fixture() {
        tx(() -> {
            Order order = Order.builder().orderCode("QC-IT-" + UUID.randomUUID()).orderType("POS")
                    .status(OrderStatus.COMPLETED).paymentStatus(PaymentStatus.PAID)
                    .returnStatus(OrderReturnStatus.NONE).originalAmount(new BigDecimal("500"))
                    .discountAmount(BigDecimal.ZERO).shippingFee(BigDecimal.ZERO).finalAmount(new BigDecimal("500"))
                    .paidAmount(new BigDecimal("500")).debtAmount(BigDecimal.ZERO).build();
            OrderItem item = OrderItem.builder().productVariantId(UUID.randomUUID()).productNameSnapshot("Isolated QC test")
                    .quantity(new BigDecimal("5")).originalUnitPrice(new BigDecimal("100"))
                    .actualUnitPrice(new BigDecimal("100")).discountAmount(BigDecimal.ZERO).lineTotal(new BigDecimal("500")).build();
            order.addItem(item);
            entityManager.persist(order);
            entityManager.flush();
            orderId = order.getId();
            itemId = item.getId();
            return null;
        });
    }

    private UUID create(String quantity) {
        return service.createReturn(orderId, CreateOrderReturnRequest.builder().refundRequired(true)
                .items(List.of(CreateOrderReturnItemRequest.builder().orderItemId(itemId)
                        .quantity(new BigDecimal(quantity)).reason(ReturnReason.DEFECTIVE)
                        .condition(ReturnCondition.SEALED).build())).build()).getId();
    }

    private <T> T tx(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    @Test
    void concurrentDispositionIdentitiesCannotReserveTheSameLastUnit() {
        UUID returnId = create("1");
        service.receiveReturn(returnId);
        UUID returnItemId = tx(() -> returns.findById(returnId).orElseThrow().getItems().getFirst().getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            tx(() -> {
                returns.findByIdAndIsDeletedFalseForUpdate(returnId).orElseThrow();
                for (int index = 0; index < 2; index++) {
                    results.add(executor.submit(() -> {
                        try {
                            service.submitInventoryDisposition(returnId, disposition(returnItemId));
                            return true;
                        } catch (InvalidOrderReturnException rejected) {
                            return false;
                        }
                    }));
                }
                // Both transactions passed their initial non-locking identity read
                // before either can reserve. This reproduces a stale MySQL RR snapshot.
                await().atMost(Duration.ofSeconds(8)).until(() -> IsolatedMySql.lockWaits() >= 2);
                return null;
            });
            long accepted = 0;
            for (var result : results) {
                if (result.get(15, TimeUnit.SECONDS)) accepted++;
            }
            assertEquals(1, accepted);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertEquals(0, BigDecimal.ONE.compareTo(commands.sumCommandedQuantityByReturnItemId(returnItemId)));
        assertEquals(1, commands.findByOrderReturnIdWithLines(returnId).size());
    }

    @Test
    void historicalFailedCommandRetainsReservationAndCannotBeRetriedWithNewIdentity() {
        UUID returnId = create("1");
        service.receiveReturn(returnId);
        UUID returnItemId = tx(() -> returns.findById(returnId).orElseThrow().getItems().getFirst().getId());
        var original = disposition(returnItemId);
        service.submitInventoryDisposition(returnId, original);
        tx(() -> {
            commands.findByCommandIdForUpdate(original.getCommandId()).orElseThrow()
                    .setStatus(OrderReturnInventoryCommandStatus.FAILED);
            return null;
        });
        assertThrows(InvalidOrderReturnException.class,
                () -> service.submitInventoryDisposition(returnId, disposition(returnItemId)));
        assertEquals(0, BigDecimal.ONE.compareTo(commands.sumCommandedQuantityByReturnItemId(returnItemId)));
        assertFalse(service.getInventoryDisposition(returnId, original.getCommandId()).isExecutionAllowed());
        assertEquals(OrderReturnInventoryCommandStatus.FAILED,
                service.submitInventoryDisposition(returnId, original).getStatus());
    }

    private SubmitReturnInventoryDispositionRequest disposition(UUID returnItemId) {
        var line = new SubmitReturnInventoryDispositionLineRequest();
        line.setReturnItemId(returnItemId);
        line.setQuantity(BigDecimal.ONE);
        line.setResult(ReturnInventoryDispositionResult.WRITE_OFF);
        var request = new SubmitReturnInventoryDispositionRequest();
        request.setCommandId(UUID.randomUUID());
        request.setLines(List.of(line));
        return request;
    }

}
