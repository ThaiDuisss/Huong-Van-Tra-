package org.hvtra.identityservice.actionlog.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.logging.action.ActionLogEvent;
import org.hvtra.identityservice.actionlog.entity.ActionLog;
import org.hvtra.identityservice.actionlog.service.ActionLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Nhận action log theo lô từ topic action.log và insert batch vào bảng action_log.
 * <ul>
 *   <li>Bản ghi JSON hỏng: ghi WARN và bỏ qua, không chặn cả lô.</li>
 *   <li>Insert DB lỗi: ném exception để DefaultErrorHandler retry cả lô,
 *       hết retry thì ghi fallback ra file (xem ActionLogKafkaConfig).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kafka-config", name = "enabled", havingValue = "true")
public class ActionLogConsumer {
    private final ActionLogService actionLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${hvtra.logging.action-log.topic:action.log}",
            groupId = "${spring.application.name:identity-service}-action-log",
            containerFactory = "actionLogListenerContainerFactory"
    )
    public void handle(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<ActionLog> batch = new ArrayList<>(records.size());
        int invalid = 0;
        for (ConsumerRecord<String, String> record : records) {
            ActionLog entity = parse(record);
            if (entity == null) {
                invalid++;
            } else {
                batch.add(entity);
            }
        }
        int saved = actionLogService.saveAll(batch);
        ack.acknowledge();
        log.debug("Action log batch: received={} saved={} invalid={}", records.size(), saved, invalid);
    }



    private ActionLog parse(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Bỏ qua field lạ để producer có thể thêm trường mới mà consumer cũ không lỗi
            ActionLogEvent event = objectMapper.readerFor(ActionLogEvent.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
            return actionLogService.toEntity(event);
        } catch (Exception ex) {
            log.warn("Skip invalid action log record. partition={} offset={} reason={}",
                    record.partition(), record.offset(), ex.getMessage());
            return null;
        }
    }

}
