package org.hvtra.commonservice.common_core.kafka.consumer;

public interface KafkaSingleConsumer<T> {
    void receive(T message, String key, Integer partition, Long offset);
}