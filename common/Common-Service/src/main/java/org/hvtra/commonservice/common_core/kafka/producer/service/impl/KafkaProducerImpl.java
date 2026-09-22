package org.hvtra.commonservice.common_core.kafka.producer.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.kafka.producer.exception.KafkaProducerException;
import org.hvtra.commonservice.common_core.kafka.producer.service.KafkaProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class KafkaProducerImpl<K  , V  > implements KafkaProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;

    public KafkaProducerImpl(KafkaTemplate<K, V> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, K key, V value) {
        log.info("Sending message={} to topic={}", value, topic);
        try {
            kafkaTemplate.send(topic, key, value).
            whenComplete(((kvSendResult, throwable) -> {
                if (throwable != null) {
                    log.error(
                            "Kafka send failed. topic={}, key={}, message={}",
                            topic,
                            key,
                            value,
                            throwable
                    );
                    return;                }
                var metadata = kvSendResult.getRecordMetadata();

                log.info(
                        "Kafka message sent. topic={}, partition={}, offset={}, key={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        key
                );            }));
        } catch (Exception e) {
            log.error(
                    "Error on kafka producer with key={}, message={}",
                    key,
                    value,
                    e
            );

            throw new KafkaProducerException(
                    "Error on kafka producer with key: " + key + " and message: " + value + e
            );
        }
    }
}
