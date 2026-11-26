package org.hvtra.iventoryservice.outbox.polling;

import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.factory.InventoryOutboxEventFactory;
import org.hvtra.iventoryservice.outbox.repository.InventoryOutboxEventRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionalInventoryOutboxEventService extends OutboxEventService<InventoryOutboxEvent> {
    private final InventoryOutboxPublicationStore publications;
    private final InventoryOutboxPollingProperties settings;

    public TransactionalInventoryOutboxEventService(
            InventoryOutboxEventRepository repository,
            InventoryOutboxEventFactory factory,
            InventoryOutboxPublicationStore publications,
            InventoryOutboxPollingProperties settings
    ) {
        super(repository, factory);
        this.publications = publications;
        this.settings = settings;
    }

    @Override
    public void saveEvent(OutboxSaveCommand command) {
        if (settings.mode() != InventoryOutboxPollingProperties.Mode.POLLING
                || !PollingInventoryEvent.supports(command.eventType(), command.topic())) {
            super.saveEvent(command);
            return;
        }
        if (command.eventId() == null || command.eventId().isBlank()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Direct Inventory event requires its business transaction and event ID");
        }
        super.saveEvent(command);
        publications.enqueue(command.eventId());
    }
}
