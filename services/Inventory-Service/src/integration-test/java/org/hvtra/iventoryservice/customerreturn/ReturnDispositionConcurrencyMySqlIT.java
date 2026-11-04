package org.hvtra.iventoryservice.customerreturn;

import jakarta.persistence.EntityManager;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.impl.CustomerReturnInspectionServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnItemResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnCondition;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnInventoryCommandResponse;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.outbox.publisher.InventoryOutboxPublisher;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL transactions/locks/ledger, mocked Order HTTP and ACK publisher. */
@SpringBootTest(classes = ReturnDispositionConcurrencyMySqlIT.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "spring.sql.init.mode=never", "storage.initialization.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
                "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
                "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false", "kafka-config.enabled=false"})
@Timeout(60)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReturnDispositionConcurrencyMySqlIT {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.7")
            .withDatabaseName("inventory_return_concurrency_test").withUsername("return_it")
            .withPassword(UUID.randomUUID().toString()).withCommand("--innodb-lock-wait-timeout=15");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
    @EntityScan(basePackageClasses = {InventoryItem.class, InventoryOutboxEvent.class})
    @EnableJpaRepositories(basePackages = "org.hvtra.iventoryservice")
    @Import(CustomerReturnInspectionServiceImpl.class)
    static class TestConfig {
        @Bean OrderReturnClient order() { return mock(OrderReturnClient.class); }
        @Bean InventoryOutboxPublisher outbox() { return mock(InventoryOutboxPublisher.class); }
    }

    @Autowired CustomerReturnInspectionServiceImpl service;
    @Autowired CustomerReturnInspectionRepository inspections;
    @Autowired InventoryItemRepository items;
    @Autowired OrderReturnClient order;
    @Autowired InventoryOutboxPublisher outbox;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    private UUID returnId, returnItemId, orderItemId, variantId, branchId;
    private InventoryItem item;
    private InventoryLocation location;

    @BeforeEach
    void fixture() {
        reset(order, outbox);
        returnId = UUID.randomUUID(); returnItemId = UUID.randomUUID(); orderItemId = UUID.randomUUID();
        variantId = UUID.randomUUID(); branchId = UUID.randomUUID();
        item = new InventoryItem(); item.setReferenceId(variantId);
        item.setItemType(InventoryItemType.PRODUCT_VARIANT); item.setReorderPoint(0);
        item = items.saveAndFlush(item);
        when(order.getReturnById(returnId)).thenReturn(new OrderReturnResponse(returnId, "RET-IT", UUID.randomUUID(),
                "ORD-IT", branchId, OrderReturnStatus.RECEIVED, null, Instant.now(), Instant.now(), List.of(
                new OrderReturnItemResponse(returnItemId, orderItemId, variantId, BigDecimal.ONE, "DEFECTIVE",
                        OrderReturnCondition.SEALED, null, "SKU-IT", "Isolated return item", null))));
    }

    private void configure(CustomerReturnQcResult result) {
        location = null;
        if (result != CustomerReturnQcResult.WRITE_OFF) {
            location = new InventoryLocation(); location.setBranchId(branchId);
            location.setCode("QC-" + UUID.randomUUID()); location.setName("Isolated test location");
            location.setType(result == CustomerReturnQcResult.DAMAGED ? InventoryLocationType.DAMAGED : InventoryLocationType.SHELF);
            tx(() -> { entityManager.persist(location); return null; });
        }
        when(order.getInventoryDisposition(eq(returnId), any())).thenAnswer(call -> new OrderReturnInventoryCommandResponse(
                call.getArgument(1), returnId, "PENDING", true, List.of(new OrderReturnInventoryCommandResponse.Line(
                returnItemId, orderItemId, variantId, BigDecimal.ONE, result.name(), location == null ? null : location.getId()))));
    }

    private CreateCustomerReturnInspectionRequest request(CustomerReturnQcResult result) {
        return new CreateCustomerReturnInspectionRequest(null, List.of(new CreateCustomerReturnInspectionLineRequest(
                returnItemId, BigDecimal.ONE, result, location == null ? null : location.getId(), "Isolated concurrency QC",
                result == CustomerReturnQcResult.RESTOCK ? CustomerReturnActualCondition.SEALED : CustomerReturnActualCondition.DAMAGED)));
    }

    @ParameterizedTest
    @EnumSource(value = CustomerReturnQcResult.class, names = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
    void twoFirstCommandIdsCannotAllocateTheSameUnit(CustomerReturnQcResult result) throws Exception {
        configure(result);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> allocate(start, result));
            Future<Boolean> second = executor.submit(() -> allocate(start, result));
            assertNotEquals(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
        }
        assertEquals(1, inspections.findQcProgressByReturnId(returnId).size());
        assertEquals(0L, ledgerCount());
    }

    private boolean allocate(CyclicBarrier start, CustomerReturnQcResult result) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            service.createInspection(returnId, UUID.randomUUID(), request(result));
            return true;
        } catch (BadRequestException rejected) {
            assertTrue(rejected.getMessage().contains("exceeds returned quantity"));
            return false;
        }
    }

    @ParameterizedTest
    @EnumSource(value = CustomerReturnQcResult.class, names = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
    void concurrentPostingAndReplayWriteExactlyOneLedgerEntry(CustomerReturnQcResult result) throws Exception {
        configure(result);
        UUID inspectionId = service.createInspection(returnId, UUID.randomUUID(), request(result)).id();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> post(start, inspectionId));
            Future<?> second = executor.submit(() -> post(start, inspectionId));
            first.get(25, TimeUnit.SECONDS); second.get(25, TimeUnit.SECONDS);
        }
        post(null, inspectionId);
        assertEquals(1L, ledgerCount());
        assertEquals(CustomerReturnInspectionStatus.POSTED, service.getInspection(inspectionId).status());
        assertEquals(0, service.getQcSummary(returnId).lines().getFirst().remainingQcQuantity().compareTo(BigDecimal.ZERO));
        if (result != CustomerReturnQcResult.WRITE_OFF) {
            BigDecimal balance = jdbc.queryForObject("SELECT SUM(quantity_on_hand) FROM inventory_balances WHERE inventory_item_id=UUID_TO_BIN(?)",
                    BigDecimal.class, item.getId().toString());
            assertEquals(0, BigDecimal.ONE.compareTo(balance));
        }
    }

    private void post(CyclicBarrier start, UUID inspectionId) {
        authenticate();
        try {
            if (start != null) start.await(5, TimeUnit.SECONDS);
            service.postInspection(inspectionId);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate() {
        InternalUserPrincipal principal = new InternalUserPrincipal(UUID.randomUUID().toString(), "qc-it", "test", Set.of("QC"), "qc-it");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_QC"))));
    }

    private long ledgerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions WHERE reference_id=?", Long.class, returnId.toString());
    }

    private <T> T tx(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

}
