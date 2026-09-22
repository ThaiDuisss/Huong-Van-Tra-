package org.hvtra.commonservice.common_core.kafka.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-consumer-config")
@ConditionalOnProperty(prefix = "kafka-consumer-config",
                        name = "enabled",
                        havingValue = "true"
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KafkaConsumerConfigData {
     private String keyDeserializer;
     private String valueDeserializer;
     private String groupId;
     private String autoOffsetReset;
//     private String specificAvroReaderKey;
//     private String specificAvroReader;
     private Boolean batchListener;
     private Boolean autoStartup;
     private Integer concurrencyLevel;
     private Integer sessionTimeoutMs;
     private Integer heartbeatIntervalMs;
     private Integer maxPollIntervalMs;
     private Long pollTimeoutMs;
     private Integer maxPollRecords;
     private Integer maxPartitionFetchBytesDefault;
     private Integer maxPartitionFetchBytesBoostFactor;
}
