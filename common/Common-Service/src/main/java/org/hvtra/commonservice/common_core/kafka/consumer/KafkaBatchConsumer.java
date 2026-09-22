package org.hvtra.commonservice.kafka.consumer;

import java.util.List;

public interface KafkaBatchConsumer<T> {
    void receive(List<T> messages, List<String> keys, List<Integer> partitions, List<Long> offsets);
}