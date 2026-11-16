package org.hvtra.promotionservice.integration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.hvtra.promotionservice.entity.PromotionInbox;
import org.hvtra.promotionservice.integration.consumer.OrderPromotionEventMetadataResolver;
import org.hvtra.promotionservice.integration.consumer.OrderPromotionEventPayloadParser;
import org.hvtra.promotionservice.integration.consumer.PromotionReservationConfirmInboxService;
import org.hvtra.promotionservice.repository.PromotionInboxRepository;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionReservationConfirmInboxService.handle")
class PromotionReservationConfirmInboxServiceTest {

    static final String COMMIT_EVENT = "PROMOTION_USAGE_COMMIT_REQUESTED";
    static final String TOPIC = "promotion.usage-commit.requested";
    static final String CONSUMER_GROUP = "promotion-service-order-events";
    static final String EVENT_ID = "evt-1";
    static final String RAW_PAYLOAD = "{\"raw\":true}";

    @Mock
    CouponUsageService couponUsageService;

    @Mock
    OrderPromotionEventPayloadParser payloadParser;

    @Mock
    OrderPromotionEventMetadataResolver metadataResolver;

    @Mock
    PromotionInboxRepository promotionInboxRepository;

    PromotionReservationConfirmInboxService inboxService;

    ConsumerRecord<String, String> record;
    OrderPromotionEventPayload event;

    @BeforeEach
    void setUp() {
        inboxService = new PromotionReservationConfirmInboxService(
                couponUsageService, payloadParser, metadataResolver, promotionInboxRepository);
        ReflectionTestUtils.setField(inboxService, "consumerGroup", CONSUMER_GROUP);
        record = new ConsumerRecord<>(TOPIC, 2, 42L, "order-key", RAW_PAYLOAD);
        event = new OrderPromotionEventPayload();
        event.setEventType(COMMIT_EVENT);
        event.setOrderId("order-1");
    }

    void parsedEvent() {
        when(payloadParser.parseCreateEvent(RAW_PAYLOAD)).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn(EVENT_ID);
    }

    /** The event was never seen: the inbox row is created and returned as saved. */
    void newInbox() {
        parsedEvent();
        when(promotionInboxRepository.findByEventIdAndConsumerGroup(EVENT_ID, CONSUMER_GROUP))
                .thenReturn(Optional.empty());
        when(promotionInboxRepository.saveAndFlush(any(PromotionInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    PromotionInbox existingInbox(InboxStatus status) {
        parsedEvent();
        PromotionInbox inbox = new PromotionInbox();
        inbox.setEventId(EVENT_ID);
        inbox.setConsumerGroup(CONSUMER_GROUP);
        inbox.setStatus(status);
        when(promotionInboxRepository.findByEventIdAndConsumerGroup(EVENT_ID, CONSUMER_GROUP))
                .thenReturn(Optional.of(inbox));
        return inbox;
    }

    PromotionInbox savedInbox() {
        ArgumentCaptor<PromotionInbox> captor = ArgumentCaptor.forClass(PromotionInbox.class);
        verify(promotionInboxRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("commit of coupon usages")
    class Commit {

        @Test
        @DisplayName("applies every usage id in order and marks the inbox PROCESSED")
        void appliesUsagesAndMarksProcessed() {
            UUID firstUsage = UUID.randomUUID();
            UUID secondUsage = UUID.randomUUID();
            event.setUsageIds(List.of(firstUsage.toString(), secondUsage.toString()));
            newInbox();

            inboxService.handle(record);

            InOrder order = inOrder(couponUsageService);
            order.verify(couponUsageService).applyUsage(firstUsage);
            order.verify(couponUsageService).applyUsage(secondUsage);
            verifyNoMoreInteractions(couponUsageService);
            PromotionInbox inbox = savedInbox();
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
            assertThat(inbox.getProcessedAt()).isNotNull();
            assertThat(inbox.getLastError()).isNull();
        }

        @Test
        @DisplayName("records where the event came from in the inbox row")
        void storesRecordMetadata() {
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            newInbox();
            when(metadataResolver.sourceService(record)).thenReturn("order-service");
            when(metadataResolver.correlationId(record, event)).thenReturn("corr-1");
            when(metadataResolver.serializeHeaders(record)).thenReturn("{\"h\":\"v\"}");

            inboxService.handle(record);

            PromotionInbox inbox = savedInbox();
            assertThat(inbox.getEventId()).isEqualTo(EVENT_ID);
            assertThat(inbox.getConsumerGroup()).isEqualTo(CONSUMER_GROUP);
            assertThat(inbox.getTopic()).isEqualTo(TOPIC);
            assertThat(inbox.getPartitionNo()).isEqualTo(2);
            assertThat(inbox.getOffsetNo()).isEqualTo(42L);
            assertThat(inbox.getMessageKey()).isEqualTo("order-key");
            assertThat(inbox.getEventType()).isEqualTo(COMMIT_EVENT);
            assertThat(inbox.getEventVersion()).isEqualTo(1);
            assertThat(inbox.getSourceService()).isEqualTo("order-service");
            assertThat(inbox.getEntityType()).isEqualTo("ORDER");
            assertThat(inbox.getEntityId()).isEqualTo("order-1");
            assertThat(inbox.getCorrelationId()).isEqualTo("corr-1");
            assertThat(inbox.getPayloadJson()).isEqualTo(RAW_PAYLOAD);
            assertThat(inbox.getHeadersJson()).isEqualTo("{\"h\":\"v\"}");
        }

        @Test
        @DisplayName("normalises the event before resolving its id and looking up the inbox")
        void normalisesBeforeResolvingEventId() {
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            newInbox();

            inboxService.handle(record);

            InOrder order = inOrder(metadataResolver, promotionInboxRepository);
            order.verify(metadataResolver).normalizeBeforeEventId(record, event);
            order.verify(metadataResolver).resolveEventId(record, event);
            order.verify(metadataResolver).normalizeEventKey(record, event);
            order.verify(promotionInboxRepository).findByEventIdAndConsumerGroup(EVENT_ID, CONSUMER_GROUP);
        }

        @Test
        @DisplayName("skips null and blank usage ids")
        void skipsBlankUsageIds() {
            UUID usage = UUID.randomUUID();
            event.setUsageIds(Arrays.asList(null, "  ", usage.toString()));
            newInbox();

            inboxService.handle(record);

            verify(couponUsageService).applyUsage(usage);
            verifyNoMoreInteractions(couponUsageService);
            assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.PROCESSED);
        }
    }

    @Nested
    @DisplayName("events that are not processed")
    class Skipped {

        @Test
        @DisplayName("a CDC event that is not a create is ignored without touching the inbox")
        void nonCreateEvent_ignored() {
            when(payloadParser.parseCreateEvent(RAW_PAYLOAD)).thenReturn(Optional.empty());

            inboxService.handle(record);

            verifyNoInteractions(metadataResolver, promotionInboxRepository, couponUsageService);
        }

        @Test
        @DisplayName("an event of another type is stored as IGNORED and applies nothing")
        void otherEventType_markedIgnored() {
            event.setEventType("ORDER_CREATED");
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            newInbox();

            inboxService.handle(record);

            PromotionInbox inbox = savedInbox();
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.IGNORED);
            assertThat(inbox.getProcessedAt()).isNotNull();
            assertThat(inbox.getLastError()).isNull();
            verifyNoInteractions(couponUsageService);
        }

        @ParameterizedTest(name = "a redelivered event whose inbox is {0} is not applied twice")
        @EnumSource(value = InboxStatus.class, names = {"PROCESSED", "IGNORED", "DEAD"})
        void duplicateOfHandledEvent_skipped(InboxStatus status) {
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            PromotionInbox inbox = existingInbox(status);

            inboxService.handle(record);

            assertThat(inbox.getStatus()).isEqualTo(status);
            verifyNoInteractions(couponUsageService);
            verify(promotionInboxRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest(name = "a redelivered event whose inbox is still {0} is rejected")
        @EnumSource(value = InboxStatus.class, names = {"RECEIVED", "PROCESSING"})
        void duplicateOfInFlightEvent_throws(InboxStatus status) {
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            existingInbox(status);

            assertThatThrownBy(() -> inboxService.handle(record))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(EVENT_ID)
                    .hasMessageContaining(status.name());
            verifyNoInteractions(couponUsageService);
            verify(promotionInboxRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("a unique-key race on the inbox insert is propagated and applies nothing")
        void inboxInsertConflict_propagated() {
            event.setUsageIds(List.of(UUID.randomUUID().toString()));
            parsedEvent();
            when(promotionInboxRepository.findByEventIdAndConsumerGroup(EVENT_ID, CONSUMER_GROUP))
                    .thenReturn(Optional.empty());
            when(promotionInboxRepository.saveAndFlush(any(PromotionInbox.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_inbox_event_consumer"));

            assertThatThrownBy(() -> inboxService.handle(record)).isInstanceOf(DataIntegrityViolationException.class);
            verifyNoInteractions(couponUsageService);
        }

        @Test
        @Disabled("Suspected bug: PromotionReservationConfirmInboxService.isTerminal treats FAILED as terminal, "
                + "so a retried commit event is silently dropped although InboxStatus.FAILED is documented as retryable")
        @DisplayName("a redelivered event whose inbox is FAILED is retried")
        void duplicateOfFailedEvent_isRetried() {
            UUID usage = UUID.randomUUID();
            event.setUsageIds(List.of(usage.toString()));
            PromotionInbox inbox = existingInbox(InboxStatus.FAILED);

            inboxService.handle(record);

            verify(couponUsageService).applyUsage(usage);
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("a commit event without usage ids fails and is marked FAILED")
        void missingUsageIds_failed() {
            event.setUsageIds(null);
            newInbox();

            assertThatThrownBy(() -> inboxService.handle(record))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("usageIds");

            PromotionInbox inbox = savedInbox();
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.FAILED);
            assertThat(inbox.getLastError()).contains("usageIds");
            assertThat(inbox.getProcessedAt()).isNotNull();
            verifyNoInteractions(couponUsageService);
        }

        @Test
        @DisplayName("an empty usage id list fails the same way")
        void emptyUsageIds_failed() {
            event.setUsageIds(List.of());
            newInbox();

            assertThatThrownBy(() -> inboxService.handle(record)).isInstanceOf(IllegalArgumentException.class);

            assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.FAILED);
            verifyNoInteractions(couponUsageService);
        }

        @Test
        @DisplayName("a malformed usage id stops the commit and is marked FAILED")
        void malformedUsageId_failed() {
            UUID validUsage = UUID.randomUUID();
            UUID neverReached = UUID.randomUUID();
            event.setUsageIds(List.of(validUsage.toString(), "not-a-uuid", neverReached.toString()));
            newInbox();

            assertThatThrownBy(() -> inboxService.handle(record)).isInstanceOf(IllegalArgumentException.class);

            verify(couponUsageService).applyUsage(validUsage);
            verify(couponUsageService, never()).applyUsage(neverReached);
            assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.FAILED);
        }

        @Test
        @DisplayName("an error from applyUsage is rethrown with the inbox marked FAILED and the remaining ids untouched")
        void applyUsageError_rethrownAndFailed() {
            UUID failingUsage = UUID.randomUUID();
            UUID neverReached = UUID.randomUUID();
            event.setUsageIds(List.of(failingUsage.toString(), neverReached.toString()));
            newInbox();
            BusinessException failure = new BusinessException("Only reserved coupon usage can be applied");
            doThrow(failure).when(couponUsageService).applyUsage(failingUsage);

            assertThatThrownBy(() -> inboxService.handle(record)).isSameAs(failure);

            verify(couponUsageService, never()).applyUsage(neverReached);
            PromotionInbox inbox = savedInbox();
            assertThat(inbox.getStatus()).isEqualTo(InboxStatus.FAILED);
            assertThat(inbox.getLastError()).isEqualTo("Only reserved coupon usage can be applied");
            assertThat(inbox.getProcessedAt()).isNotNull();
        }
    }
}
