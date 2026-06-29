package org.hvtra.commonservice.outbox.resolver;

public interface OutboxTopicResolver {

    String resolveTopic(String eventType);
}