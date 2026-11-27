package org.hvtra.orderservice.outbox.polling;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Delivery metadata is separate from the immutable event that other Outbox routes still use.
 */
@Repository
@RequiredArgsConstructor
public class OrderOutboxPublicationStore {
    private static final String DUE = """
            state IN ('PENDING', 'RETRY', 'IN_FLIGHT') AND available_at <= UTC_TIMESTAMP(6)
            """;

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventId) {
        int inserted = jdbc.update("""
                INSERT INTO order_outbox_publication (outbox_id, state, available_at)
                SELECT id, 'PENDING', UTC_TIMESTAMP(6) FROM outbox_events WHERE event_id = ?
                """, eventId);
        if (inserted != 1) {
            throw new IllegalStateException("Order Outbox event is missing from its transaction");
        }
    }

    public List<String> due(int limit) {
        return jdbc.queryForList("""
                SELECT BIN_TO_UUID(outbox_id) FROM order_outbox_publication WHERE
                """ + DUE + " ORDER BY available_at, outbox_id LIMIT ?", String.class, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PollingOrderEvent> claim(String id, long leaseMs) {
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE order_outbox_publication
                SET state = 'IN_FLIGHT', claim_token = ?, attempts = LEAST(attempts + 1, 1000000),
                    available_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                WHERE outbox_id = UUID_TO_BIN(?) AND
                """ + DUE, token, leaseMs * 1000, id);
        if (claimed == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT p.attempts, o.event_id, o.event_type, o.event_version, o.aggregate_type, o.aggregate_id,
                       o.message_key, o.business_key, o.topic, o.source_service, o.payload, o.occurred_at,
                       o.saga_id, o.correlation_id, o.causation_id
                FROM order_outbox_publication p JOIN outbox_events o ON o.id = p.outbox_id
                WHERE p.outbox_id = UUID_TO_BIN(?)
                """, (rs, row) -> new PollingOrderEvent(id, token, rs.getInt("attempts"), rs.getString("event_id"),
                rs.getString("event_type"), rs.getInt("event_version"), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getString("message_key"), rs.getString("business_key"),
                rs.getString("topic"), rs.getString("source_service"), rs.getString("payload"),
                rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getString("saga_id"), rs.getString("correlation_id"), rs.getString("causation_id")), id));
    }

    public boolean sent(PollingOrderEvent event) {
        return jdbc.update("""
                UPDATE order_outbox_publication SET state = 'SENT', sent_at = UTC_TIMESTAMP(6),
                    claim_token = NULL, last_error = NULL
                WHERE outbox_id = UUID_TO_BIN(?) AND state = 'IN_FLIGHT' AND claim_token = ?
                """, event.outboxId(), event.claimToken()) == 1;
    }

    public boolean retry(PollingOrderEvent event, long delayMs, String error) {
        return jdbc.update("""
                UPDATE order_outbox_publication SET state = 'RETRY', claim_token = NULL, last_error = ?,
                    available_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                WHERE outbox_id = UUID_TO_BIN(?) AND state = 'IN_FLIGHT' AND claim_token = ?
                """, error, delayMs * 1000, event.outboxId(), event.claimToken()) == 1;
    }

    public boolean quarantine(PollingOrderEvent event, String error) {
        return jdbc.update("""
                UPDATE order_outbox_publication SET state = 'QUARANTINED', claim_token = NULL, last_error = ?
                WHERE outbox_id = UUID_TO_BIN(?) AND state = 'IN_FLIGHT' AND claim_token = ?
                """, error, event.outboxId(), event.claimToken()) == 1;
    }
}
