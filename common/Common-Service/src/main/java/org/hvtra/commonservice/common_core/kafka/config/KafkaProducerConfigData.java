package org.hvtra.commonservice.common_core.kafka.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-producer-config")
@ConditionalOnProperty(prefix = "kafka-producer-config",
                        name = "enabled",
                        havingValue = "true"
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KafkaProducerConfigData {
     private String keySerializerClass;
     private String valueSerializerClass;
     private String compressionType;
     private String acks;
     private Integer batchSize;
     private Integer batchSizeBoostFactor;
     private Integer lingerMs;
     private Integer requestTimeoutMs;
     private Integer retryCount;
}
