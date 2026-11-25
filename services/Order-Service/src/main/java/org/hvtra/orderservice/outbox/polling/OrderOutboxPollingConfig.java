package org.hvtra.orderservice.outbox.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hvtra.orderservice.outbox.service.OrderKafkaSigner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "order.outbox.publisher", name = "mode", havingValue = "POLLING")
@EnableConfigurationProperties(KafkaProperties.class)
class OrderOutboxPollingConfig {
    @Bean(initMethod = "start", destroyMethod = "close")
    @DependsOnDatabaseInitialization
    OrderOutboxPollingPublisher orderOutboxPollingPublisher(
            OrderOutboxPublicationStore publications, ObjectMapper mapper, OrderKafkaSigner signer,
            OrderOutboxPollingProperties settings, KafkaProperties kafka, ObjectProvider<SslBundles> sslBundles,
            @Value("${order.outbox.publisher.bootstrap-servers:${KAFKA_BOOTSTRAP_SERVERS:}}") String bootstrapServers) {
        if (!StringUtils.hasText(bootstrapServers)) {
            throw new IllegalStateException("Order Outbox polling requires explicit Kafka bootstrap servers");
        }
        signer.requireConfiguredKey();
        Map<String, Object> properties = kafka.buildProducerProperties(sslBundles.getIfAvailable());
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "order-outbox-poll");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, settings.ackTimeoutMs());
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.min(5000, settings.ackTimeoutMs()));
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (long) settings.ackTimeoutMs());
        // Preserve session/security settings without exposing a producer bean to existing Kafka routes.
        properties.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(properties);
        factory.setPhysicalCloseTimeout(5);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
        template.setProducerListener(null);
        return new OrderOutboxPollingPublisher(publications, new OrderOutboxRecordFactory(mapper, signer),
                template, settings, factory::destroy);
    }
}
