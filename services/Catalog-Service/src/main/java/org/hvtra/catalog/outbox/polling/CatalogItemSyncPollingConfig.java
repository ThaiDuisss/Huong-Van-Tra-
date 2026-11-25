package org.hvtra.catalog.outbox.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
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
@ConditionalOnProperty(prefix = "catalog.outbox.item-sync", name = "mode", havingValue = "POLLING")
@EnableConfigurationProperties(KafkaProperties.class)
class CatalogItemSyncPollingConfig {
    @Bean(initMethod = "start", destroyMethod = "close")
    @DependsOnDatabaseInitialization
    CatalogItemSyncPollingPublisher catalogItemSyncPollingPublisher(
            CatalogItemSyncPublicationStore publications, ObjectMapper mapper, CatalogItemSyncProperties settings,
            KafkaProperties kafka, ObjectProvider<SslBundles> sslBundles,
            @Value("${catalog.outbox.item-sync.bootstrap-servers:}") String bootstrapServers) {
        if (!StringUtils.hasText(bootstrapServers)) {
            throw new IllegalStateException("ITEM_SYNC polling requires explicit Kafka bootstrap servers");
        }

        Map<String, Object> props = kafka.buildProducerProperties(sslBundles.getIfAvailable());
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "catalog-item-sync-poll");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, settings.ackTimeoutMs());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.min(5000, settings.ackTimeoutMs()));
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (long) settings.ackTimeoutMs());
        props.put("partitioner.ignore.keys", false);
        props.remove(ProducerConfig.PARTITIONER_CLASS_CONFIG);
        props.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);

        // Dedicated producer, without adding or replacing the service's shared Kafka beans.
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        factory.setPhysicalCloseTimeout(5);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
        template.setProducerListener(null);
        return new CatalogItemSyncPollingPublisher(publications, new CatalogItemSyncRecordFactory(mapper),
                template, settings, factory::destroy);
    }
}
