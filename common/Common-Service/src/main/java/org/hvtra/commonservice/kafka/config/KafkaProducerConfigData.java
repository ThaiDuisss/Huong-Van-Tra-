package org.hvtra.commonservice.kafka.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-config")
@ConditionalOnProperty(prefix = "kafka-config",
                        name = "enabled",
                        havingValue = "true"
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KafkaProducerConfigData {
     String bootstrapServers;
//     String schemaRegistryUrlKey;
//     String schemaRegistryUrl;
     Integer numOfPartitions;
     Short replicationFactor;
}
