package org.hvtra.commonservice.common_core.kafka.outbox.resolver;

public interface OutboxTopicResolver {

    String resolveTopic(String eventType);
}