package org.hvtra.catalog.outbox.polling;

import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.catalog.outbox.factory.CatalogOutboxEventFactory;
import org.hvtra.catalog.outbox.repository.CatalogOutboxEventRepository;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionalCatalogOutboxEventService extends OutboxEventService<CatalogOutboxEvent> {
    private final CatalogItemSyncPublicationStore publications;
    private final CatalogItemSyncProperties settings;

    public TransactionalCatalogOutboxEventService(CatalogOutboxEventRepository repository,
                                                   CatalogOutboxEventFactory factory,
                                                   CatalogItemSyncPublicationStore publications,
                                                   CatalogItemSyncProperties settings) {
        super(repository, factory);
        this.publications = publications;
        this.settings = settings;
    }

    @Override
    public void saveEvent(OutboxSaveCommand command) {
        if (settings.mode() == CatalogItemSyncProperties.Mode.CDC || !CatalogItemSyncEvent.supports(command.eventType())) {
            super.saveEvent(command);
            return;
        }
        if (!CatalogItemSyncEvent.TOPIC.equals(command.topic()) || !CatalogItemSyncEvent.SOURCE.equals(command.sourceService())
                || command.messageKey() == null || !command.messageKey().equals(command.aggregateId())
                || command.eventId() == null || command.eventId().isBlank()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ITEM_SYNC capture requires the configured route, event identity and business transaction");
        }
        publications.lockStream(command.messageKey());
        super.saveEvent(command);
        publications.enqueue(command.eventId(), command.messageKey());
    }
}
