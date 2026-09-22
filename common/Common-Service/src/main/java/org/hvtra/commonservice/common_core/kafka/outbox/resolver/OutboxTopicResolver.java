package org.hvtra.commonservice.kafka.outbox.resolver;

public interface OutboxTopicResolver {

    String resolveTopic(String eventType);
}