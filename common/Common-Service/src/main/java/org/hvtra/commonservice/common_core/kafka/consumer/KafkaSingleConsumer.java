package org.hvtra.commonservice.kafka.consumer;

public interface KafkaSingleConsumer<T> {
    void receive(T message, String key, Integer partition, Long offset);
}