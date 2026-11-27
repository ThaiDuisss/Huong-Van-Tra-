package org.hvtra.catalog.integration.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
@EnableKafka
public class CatalogKafkaConsumerConfig {
}
