package org.hvtra.orderservice.returnrefund;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.outbox.OrderOutboxConfig;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.impl.OrderReturnInventoryCommandReconciliationWriter;
import org.hvtra.orderservice.service.impl.OrderReturnRefundTransactionServiceImpl;
import org.hvtra.orderservice.service.impl.OrderReturnServiceImpl;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnCreationEligibilityResolver;
import org.hvtra.orderservice.service.support.OrderReturnDisplayStatusResolver;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgressResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
@EntityScan(basePackageClasses = Order.class)
@EnableJpaRepositories(basePackageClasses = OrderRepository.class)
@EnableAspectJAutoProxy
@Import({OrderReturnServiceImpl.class, OrderReturnRefundTransactionServiceImpl.class,
        OrderReturnInventoryCommandReconciliationWriter.class, OrderCodeGenerator.class,
        ReturnRefundCalculator.class, OrderReturnMapper.class, OrderReturnHistoryWriter.class,
        OrderReturnPhysicalCompletionHandler.class, OrderReturnPhysicalProgressResolver.class,
        OrderReturnRefundEligibilityResolver.class, OrderReturnCreationEligibilityResolver.class,
        OrderReturnDisplayStatusResolver.class, OrderOutboxPublisher.class, OrderOutboxConfig.class,
        OrderOutboxEventFactory.class, OrderOutboxTopicResolver.class, TestBoundaryProbe.class})
class ReturnRefundTestConfiguration {
    @Bean PaymentClient paymentClient() { return mock(PaymentClient.class); }
    @Bean InventoryClient inventoryClient() { return mock(InventoryClient.class); }

    @Bean FlywayMigrationStrategy guardedMigrations() {
        return flyway -> {
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                IsolatedMySql.verify(connection);
            } catch (Exception exception) {
                throw new IllegalStateException("BLOCKED: isolation preflight failed before Flyway", exception);
            }
            try {
                flyway.migrate();
            } catch (RuntimeException migrationFailure) {
                // Diagnostics are limited to the already verified container/schema; do not repair or clean.
                try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                     var statement = connection.createStatement()) {
                    IsolatedMySql.verify(connection);
                    List<Map<String, Object>> history = new ArrayList<>();
                    try (var rows = statement.executeQuery(
                            "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank")) {
                        while (rows.next()) {
                            history.add(Map.of("version", rows.getString(1), "script", rows.getString(2),
                                    "success", rows.getBoolean(3)));
                        }
                    }
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("history", history);
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name='order_saga' AND column_name='order_id'")) {
                        rows.next(); evidence.put("orderSagaOrderIdColumnCount", rows.getInt(1));
                    }
                    try (var rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM orders), "
                            + "(SELECT COUNT(*) FROM outbox_events)")) {
                        rows.next(); evidence.put("orderRows", rows.getInt(1)); evidence.put("outboxRows", rows.getInt(2));
                    }
                    IsolatedMySql.evidence("migration-failure-state", evidence);
                } catch (Exception diagnosticFailure) {
                    migrationFailure.addSuppressed(diagnosticFailure);
                }
                throw migrationFailure;
            }
        };
    }
}
