package org.hvtra.commonservice.kafka.producer.service;

public interface KafkaProducer<K  , V  > {
    void send(String topic, K key, V value);
}
