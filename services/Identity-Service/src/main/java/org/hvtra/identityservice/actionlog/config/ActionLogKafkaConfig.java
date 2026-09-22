package org.hvtra.identityservice.actionlog.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.hvtra.commonservice.common_core.kafka.config.KafkaConfigData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Container factory riêng cho topic action.log: batch listener, ack thủ công,
 * một thread là đủ vì insert theo lô. Không phụ thuộc cấu hình kafka-consumer-config
 * dùng chung để không ảnh hưởng các consumer khác của service.
 * Chỉ tạo khi {@code kafka-config.enabled=true}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class ActionLogKafkaConfig {

    /** Fallback: batch lỗi sau khi hết retry được ghi JSON ra file action-log.log của Identity-Service. */
    private static final Logger ACTION_LOG = LoggerFactory.getLogger("ACTION_LOG");

    private final KafkaConfigData kafkaConfigData;
    private final ActionLogProperties properties;


    @Bean
    public ConsumerFactory<String, String> actionLogConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.getMaxPollRecords());
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 64 * 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 2000);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> actionLogListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(actionLogConsumerFactory());
        factory.setBatchListener(true);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(3000);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, ex) -> {
                    log.error("Action log record dropped to file after retries. topic={} partition={} offset={} reason={}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    ACTION_LOG.info(String.valueOf(record.value()));
                },
                new FixedBackOff(properties.getRetryBackoffMs(), properties.getRetryAttempts())
        );
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }



}
