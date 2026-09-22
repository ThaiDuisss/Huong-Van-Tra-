package org.hvtra.commonservice.kafka.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.kafka.outbox.factory.OutboxEventFactory;
import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class OutboxEventService<T extends BaseOutboxEvent> {

    private final BaseOutboxEventRepository<T> outboxEventRepository;
    private final OutboxEventFactory<T> outboxEventFactory;

    public void saveEvent(OutboxSaveCommand command) {
        T event = outboxEventFactory.create(
                command,
                command.eventId() == null || command.eventId().isBlank()
                        ? UUID.randomUUID().toString()
                        : command.eventId(),
                Instant.now()
        );

        outboxEventRepository.saveAndFlush(event);
    }

    public void saveEventIfAbsent(OutboxSaveCommand command) {
        if (outboxEventRepository.existsByEventTypeAndBusinessKey(
                command.eventType(),
                command.businessKey()
        )) {
            return;
        }

        try {
            saveEvent(command);
        } catch (DataIntegrityViolationException ex) {
            if (!outboxEventRepository.existsByEventTypeAndBusinessKey(
                    command.eventType(),
                    command.businessKey()
            )) {
                throw ex;
            }

            log.debug(
                    "Ignore duplicate outbox event. eventType={}, businessKey={}",
                    command.eventType(),
                    command.businessKey()
            );
        }
    }
}
