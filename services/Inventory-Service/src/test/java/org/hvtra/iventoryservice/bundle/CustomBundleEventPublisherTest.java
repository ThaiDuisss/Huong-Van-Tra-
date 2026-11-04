package org.hvtra.iventoryservice.bundle;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.iventoryservice.bundle.service.CustomBundleEventPublisher;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePacking;
import org.hvtra.iventoryservice.outbox.entity.InventoryOutboxEvent;
import org.hvtra.iventoryservice.outbox.resolver.InventoryOutboxTopicResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomBundleEventPublisher Unit Tests")
class CustomBundleEventPublisherTest {

    private static final UUID PACKING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440600");
    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440700");
    private static final UUID ORDER_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440701");

    @Mock
    private OutboxEventService<InventoryOutboxEvent> outboxEventService;

    private CustomBundleEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CustomBundleEventPublisher(outboxEventService, new InventoryOutboxTopicResolver());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("publishPacked writes a CUSTOM_BUNDLE_PACKED outbox event keyed by order")
    void publishPacked_WhenAuthenticated_ShouldSaveOutboxEvent() {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                "550e8400-e29b-41d4-a716-446655440020", "kho-test", "test", Set.of("INVENTORY_STAFF"), "corr-20261103-001");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_INVENTORY_STAFF"))));
        CustomBundlePacking packing = packing();
        packing.setPackedAt(Instant.parse("2026-11-03T03:15:00Z"));

        publisher.publishPacked(packing);

        OutboxSaveCommand command = captured();
        assertThat(command.eventId()).matches("^[0-9a-f-]{36}$");
        assertThat(command.aggregateType()).isEqualTo("CUSTOM_BUNDLE_PACKING");
        assertThat(command.aggregateId()).isEqualTo(PACKING_ID.toString());
        assertThat(command.messageKey()).isEqualTo(ORDER_ID.toString());
        assertThat(command.eventType()).isEqualTo("CUSTOM_BUNDLE_PACKED");
        assertThat(command.businessKey()).isEqualTo(PACKING_ID + ":PACKED");
        assertThat(command.topic()).isEqualTo("inventory.event.CUSTOM_BUNDLE_PACKED");
        assertThat(command.sourceService()).isEqualTo("inventory-service");
        assertThat(command.correlationId()).isEqualTo("corr-20261103-001");
        assertThat(command.causationId()).isEqualTo(PACKING_ID.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) command.payload();
        assertThat(payload).containsEntry("packingCode", "DGB-00001")
                .containsEntry("orderCode", "DH-00001")
                .containsEntry("orderItemId", ORDER_ITEM_ID.toString())
                .containsEntry("issueTransactionCode", "PXB-00001")
                .containsEntry("packedAt", "2026-11-03T03:15:00Z");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) command.headers();
        assertThat(headers).containsEntry("event_id", command.eventId())
                .containsEntry("event_version", 1)
                .containsEntry("aggregate_id", PACKING_ID.toString());
    }

    @Test
    @DisplayName("publishPacked from a system call has no correlation id and no packedAt")
    void publishPacked_WhenNoAuthentication_ShouldLeaveCorrelationEmpty() {
        publisher.publishPacked(packing());

        OutboxSaveCommand command = captured();
        assertThat(command.correlationId()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) command.payload();
        assertThat(payload).containsEntry("packedAt", null);
    }

    private OutboxSaveCommand captured() {
        ArgumentCaptor<OutboxSaveCommand> captor = ArgumentCaptor.forClass(OutboxSaveCommand.class);
        verify(outboxEventService).saveEventIfAbsent(captor.capture());
        return captor.getValue();
    }

    private CustomBundlePacking packing() {
        CustomBundlePacking packing = new CustomBundlePacking();
        packing.setId(PACKING_ID);
        packing.setPackingCode("DGB-00001");
        packing.setOrderId(ORDER_ID);
        packing.setOrderCode("DH-00001");
        packing.setOrderItemId(ORDER_ITEM_ID);
        packing.setIssueTransactionCode("PXB-00001");
        return packing;
    }
}
