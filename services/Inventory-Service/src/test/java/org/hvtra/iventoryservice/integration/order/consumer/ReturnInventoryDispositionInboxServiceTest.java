package org.hvtra.iventoryservice.integration.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.integration.order.dto.ReturnInventoryDispositionRequestedEvent;
import org.hvtra.iventoryservice.integration.order.dto.ReturnInventoryDispositionRequestedLine;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.outbox.publisher.InventoryOutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnInventoryDispositionInboxServiceTest {
    @Mock private CustomerReturnInspectionService inspections;
    @Mock private InventoryInboxRepository inboxes;
    @Mock private ReturnInventoryDispositionRequestedEventParser parser;
    @Mock private EventMetadataResolver metadata;
    @Mock private PlatformTransactionManager transactions;
    @Mock private InventoryOutboxPublisher outbox;
    private ReturnInventoryDispositionInboxService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, InventoryInbox> stored = new HashMap<>();
    private UUID returnId;

    @BeforeEach
    void setUp() {
        returnId = UUID.randomUUID();
        service = new ReturnInventoryDispositionInboxService(inspections, inboxes, parser, metadata, transactions, mapper, outbox);
        ReflectionTestUtils.setField(service, "consumerGroup", "test-group");
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(parser.parseCreateEvent(anyString())).thenAnswer(call -> Optional.of(
                mapper.readValue((String) call.getArgument(0), ReturnInventoryDispositionRequestedEvent.class)));
        when(metadata.resolveEventId(any(), any(), anyString())).thenAnswer(call ->
                ((ReturnInventoryDispositionRequestedEvent) call.getArgument(1)).getEventId());
        when(inboxes.findByCommandIdAndConsumerGroupForUpdate(anyString(), eq("test-group")))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(inboxes.saveAndFlush(any())).thenAnswer(call -> {
            InventoryInbox inbox = call.getArgument(0);
            stored.put(inbox.getCommandId(), inbox);
            return inbox;
        });
        lenient().when(inspections.createInspection(eq(returnId), any(UUID.class), any())).thenAnswer(call ->
                new CustomerReturnInspectionResponse(UUID.randomUUID(), "CRI-TEST", returnId, "RET-TEST", null,
                        UUID.randomUUID(), "ORD-TEST", CustomerReturnInspectionStatus.CREATED, Instant.now(), null, null, List.of()));
    }

    private ReturnInventoryDispositionRequestedEvent event(UUID commandId) {
        ReturnInventoryDispositionRequestedEvent event = new ReturnInventoryDispositionRequestedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("RETURN_INVENTORY_DISPOSITION_REQUESTED");
        event.setCommandId(commandId.toString());
        event.setReturnId(returnId.toString());
        ReturnInventoryDispositionRequestedLine line = new ReturnInventoryDispositionRequestedLine();
        line.setReturnItemId(returnId.toString());
        line.setOrderItemId(returnId.toString());
        line.setProductVariantId(returnId.toString());
        line.setQuantity("1");
        line.setResult("WRITE_OFF");
        event.setLines(List.of(line));
        return event;
    }

    private void deliver(ReturnInventoryDispositionRequestedEvent event) throws Exception {
        service.handle(new ConsumerRecord<>("order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED", 0, 1L,
                event.getCommandId(), mapper.writeValueAsString(event)));
    }

    @Test
    void sameCommandWithSameOrNewTransportEventCreatesOnlyOneInspection() throws Exception {
        UUID commandId = UUID.randomUUID();
        ReturnInventoryDispositionRequestedEvent original = event(commandId);
        deliver(original);
        deliver(original);
        deliver(event(commandId));
        assertThat(stored).hasSize(1);
        verify(inspections, times(1)).createInspection(eq(returnId), eq(commandId), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void differentCommandIdsWithIdenticalBusinessFieldsCreateDistinctInspections() throws Exception {
        deliver(event(UUID.randomUUID()));
        deliver(event(UUID.randomUUID()));
        assertThat(stored).hasSize(2);
        assertThat(stored.values().stream().map(InventoryInbox::getInspectionId).distinct().count()).isEqualTo(2);
        verify(inspections, times(2)).createInspection(eq(returnId), any(UUID.class), any());
    }

    @ParameterizedTest
    @EnumSource(value = InboxStatus.class, names = {"RECEIVED", "PROCESSING", "PROCESSED"})
    void conflictingPayloadNeverReplacesOriginalInAnyInboxState(InboxStatus status) throws Exception {
        UUID commandId = UUID.randomUUID();
        deliver(event(commandId));
        InventoryInbox original = stored.get(commandId.toString());
        original.setStatus(status);
        String hash = original.getBusinessPayloadHash();
        String inspectionId = original.getInspectionId();
        ReturnInventoryDispositionRequestedEvent conflict = event(commandId);
        conflict.getLines().getFirst().setQuantity("2");
        deliver(conflict);
        assertThat(original.getBusinessPayloadHash()).isEqualTo(hash);
        assertThat(original.getInspectionId()).isEqualTo(inspectionId);
        assertThat(original.getStatus()).isEqualTo(status);
        assertThat(original.getLastError()).contains("conflicting payload");
        verify(inspections, times(1)).createInspection(eq(returnId), eq(commandId), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void processedReplayOnlyEnsuresTheExistingCommandAcknowledgement() throws Exception {
        UUID commandId = UUID.randomUUID();
        deliver(event(commandId));
        InventoryInbox original = stored.get(commandId.toString());
        original.setStatus(InboxStatus.PROCESSED);
        deliver(event(commandId));
        verify(inspections, times(1)).createInspection(eq(returnId), eq(commandId), any());
        verify(outbox).publishReturnInventoryDispositionProcessed(eq(commandId.toString()), eq(returnId.toString()),
                eq(original.getInspectionId()), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failureRecheckPreservesTerminalCommandAndOnlyReplaysMatchingPayload(boolean conflict) throws Exception {
        UUID commandId = UUID.randomUUID();
        when(inspections.createInspection(eq(returnId), eq(commandId), any())).thenAnswer(call -> {
            InventoryInbox concurrent = stored.get(commandId.toString());
            concurrent.setStatus(InboxStatus.PROCESSED);
            concurrent.setInspectionId(UUID.randomUUID().toString());
            if (conflict) {
                concurrent.setBusinessPayloadHash("previous-payload-hash");
            }
            throw new BadRequestException("Simulate a permanent failure before the locked recheck");
        });

        deliver(event(commandId));

        InventoryInbox original = stored.get(commandId.toString());
        assertThat(original.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(original.getInspectionId()).isNotBlank();
        if (conflict) {
            assertThat(original.getBusinessPayloadHash()).isEqualTo("previous-payload-hash");
            assertThat(original.getLastError()).contains("conflicting payload");
            verifyNoInteractions(outbox);
        } else {
            verify(outbox).publishReturnInventoryDispositionProcessed(eq(commandId.toString()), eq(returnId.toString()),
                    eq(original.getInspectionId()), any(), any());
        }
    }
}
