package org.hvtra.orderservice.outbox.polling;

import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import org.hvtra.orderservice.repository.OrderOutboxEventRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionalOrderOutboxEventService extends OutboxEventService<OrderOutboxEvent> {
    private final OrderOutboxPublicationStore publications;
    private final OrderOutboxPollingProperties settings;

    public TransactionalOrderOutboxEventService(OrderOutboxEventRepository repository, OrderOutboxEventFactory factory,
                                                OrderOutboxPublicationStore publications,
                                                OrderOutboxPollingProperties settings) {
        super(repository, factory);
        this.publications = publications;
        this.settings = settings;
    }

    @Override
    public void saveEvent(OutboxSaveCommand command) {
        if (settings.mode() != OrderOutboxPollingProperties.Mode.POLLING
                || !PollingOrderEvent.supports(command.eventType(), command.topic())) {
            super.saveEvent(command);
            return;
        }
        if (command.eventId() == null || command.eventId().isBlank()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Direct Order event requires its business transaction and event ID");
        }
        super.saveEvent(command);
        publications.enqueue(command.eventId());
    }
}
