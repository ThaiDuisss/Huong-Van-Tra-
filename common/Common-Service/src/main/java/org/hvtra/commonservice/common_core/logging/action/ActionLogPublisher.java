package org.hvtra.commonservice.logging.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hvtra.commonservice.kafka.config.KafkaConfigData;
import org.hvtra.commonservice.logging.LogMaskUtils;
import org.hvtra.commonservice.logging.LoggingProperties;
import org.hvtra.commonservice.logging.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Gửi action log lên Kafka bằng producer String/String riêng, chỉ tạo khi có
 * {@code hvtra.logging.action-log.bootstrap-servers} hoặc {@code kafka-config.enabled=true}. Không dùng KafkaTemplate chung của service vì
 * serializer mỗi service một khác, và khi service tắt Kafka thì Spring Boot vẫn
 * tự tạo một KafkaTemplate mặc định trỏ vào localhost:9092.
 * Không có Kafka hoặc gửi lỗi thì ghi JSON ra file action-log.log.
 */
@Slf4j
@Component
public class ActionLogPublisher implements DisposableBean {
    private static final Logger ACTION_LOG = LoggerFactory.getLogger("ACTION_LOG");

    private final DefaultKafkaProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LoggingProperties properties;
    @Value("${spring.application.name:unknown}")
    private final String serviceName;

    public ActionLogPublisher(ObjectProvider<KafkaConfigData> kafkaConfigDataProvider,
                              ObjectMapper objectMapper,
                              LoggingProperties properties,
                              @Value("${spring.application.name:unknown}") String serviceName) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.serviceName = serviceName;
        String bootstrapServers = resolveBootstrapServers(properties, kafkaConfigDataProvider.getIfAvailable());
        this.producerFactory = bootstrapServers == null ? null : buildProducerFactory(bootstrapServers, serviceName);
        this.kafkaTemplate = producerFactory == null ? null : new KafkaTemplate<>(producerFactory);
    }

    public void publish(ActionLogEvent actionLogEvent) {
        if(!properties.getActionLog().isEnabled() || actionLogEvent == null) {
            return;
        }
        String json = null;
        try {
            fillDefaults(actionLogEvent);
            json = objectMapper.writeValueAsString(actionLogEvent);
            String payload = json;

            if (kafkaTemplate == null) {
                ACTION_LOG.info(json);
                return;
            }
            String key = actionLogEvent.getUserId() != null ? actionLogEvent.getUserId() : actionLogEvent.getRequestId();
            kafkaTemplate.send(properties.getActionLog().getTopic(), key, payload)
                    .whenComplete((result, throwable) ->{
                        if (throwable != null) {
                            log.warn("Send action log to Kafka failed, fallback to file. action={} reason={}",
                                    actionLogEvent.getAction(), throwable.getMessage());
                            ACTION_LOG.info(payload);
                        }
                    } );
        }catch (Exception ex) {
            log.warn("Cannot publish action log action={}: {}", actionLogEvent.getAction(), ex.getMessage());
            if (json != null) {
                ACTION_LOG.info(json);
            }
        }
    }

    private static String resolveBootstrapServers(LoggingProperties properties, KafkaConfigData kafkaConfigData) {
        String own = properties.getActionLog().getBootstrapServers();
        if (own != null && !own.isBlank()) {
            return own;
        }
        return kafkaConfigData == null ? null : kafkaConfigData.getBootstrapServers();
    }

    private static DefaultKafkaProducerFactory<String, String> buildProducerFactory(String bootstrapServers,
                                                                                    String serviceName) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, serviceName + "-action-log");
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 50);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        // Kafka chết thì không được giữ thread nghiệp vụ: chờ tối đa 2s rồi rơi về ghi file
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Override
    public void destroy() {
        if (producerFactory != null) {
            producerFactory.destroy();
        }
    }

    private void fillDefaults(ActionLogEvent event) {
        if (event.getRequestId() == null) {
            event.setRequestId(MDC.get(MdcKeys.REQUEST_ID));
        }
        if (event.getUserId() == null) {
            event.setUserId(MDC.get(MdcKeys.USER_ID));
        }
        if (event.getService() == null) {
            event.setService(serviceName);
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        if (event.getStatus() == null) {
            event.setStatus("SUCCESS");
        }
        int max = properties.getActionLog().getMaxDetailLength();
        event.setDetail(LogMaskUtils.maskAndTruncate(event.getDetail(), max));
        event.setErrorMessage(LogMaskUtils.truncate(event.getErrorMessage(), 500));
    }
}



