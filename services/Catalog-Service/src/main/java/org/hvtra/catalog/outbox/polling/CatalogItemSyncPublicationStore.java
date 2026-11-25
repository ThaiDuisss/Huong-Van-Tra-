package org.hvtra.catalog.outbox.polling;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CatalogItemSyncPublicationStore {
    private static final String ELIGIBLE = """
            p.state IN ('PENDING', 'RETRY', 'IN_FLIGHT') AND p.available_at <= UTC_TIMESTAMP(6)
            AND NOT EXISTS (
                SELECT 1 FROM catalog_item_sync_publication previous
                WHERE previous.message_key = p.message_key AND previous.sequence_no < p.sequence_no
                  AND previous.state <> 'SENT'
            )
            AND EXISTS (
                SELECT 1 FROM catalog_item_sync_cutover gate
                WHERE gate.id = 1 AND gate.send_enabled = TRUE AND gate.activation_id = ?
                  AND gate.mysql_server_uuid = @@GLOBAL.server_uuid AND gate.mysql_schema = DATABASE()
            )
            """;

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockStream(String messageKey) {
        // Hold only this aggregate's lock until the business transaction commits.
        jdbc.update("""
                INSERT INTO catalog_item_sync_stream (message_key) VALUES (?)
                ON DUPLICATE KEY UPDATE message_key = catalog_item_sync_stream.message_key
                """, messageKey);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventId, String messageKey) {
        int inserted = jdbc.update("""
                INSERT INTO catalog_item_sync_publication (outbox_id, message_key, state, available_at)
                SELECT id, message_key, 'PENDING', UTC_TIMESTAMP(6) FROM outbox_events
                WHERE event_id = ? AND message_key = ? AND topic = ?
                """, eventId, messageKey, CatalogItemSyncEvent.TOPIC);
        if (inserted != 1) {
            throw new IllegalStateException("ITEM_SYNC outbox event is missing from its business transaction");
        }
    }

    public List<Long> due(UUID activationId, int limit) {
        return jdbc.queryForList("""
                SELECT p.sequence_no FROM catalog_item_sync_publication p WHERE
                """ + ELIGIBLE + " ORDER BY p.available_at, p.sequence_no LIMIT ?",
                Long.class, activationId.toString(), limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<CatalogItemSyncEvent> claim(long sequence, UUID activationId, long leaseMs) {
        List<Long> locked = jdbc.queryForList("""
                SELECT p.sequence_no FROM catalog_item_sync_publication p WHERE p.sequence_no = ? AND
                """ + ELIGIBLE + " FOR UPDATE SKIP LOCKED", Long.class, sequence, activationId.toString());
        if (locked.isEmpty()) {
            return Optional.empty();
        }

        String token = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE catalog_item_sync_publication
                SET state = 'IN_FLIGHT', claim_token = ?, attempts = LEAST(attempts + 1, 1000000),
                    available_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                WHERE sequence_no = ?
                """, token, leaseMs * 1000, sequence);
        CatalogItemSyncEvent event = jdbc.queryForObject("""
                SELECT p.attempts, o.event_id, o.event_type, o.event_version, o.source_service, o.saga_id,
                       o.correlation_id, o.causation_id, o.aggregate_type, o.aggregate_id, o.business_key,
                       o.message_key, o.topic, o.payload, o.occurred_at
                FROM catalog_item_sync_publication p JOIN outbox_events o ON o.id = p.outbox_id
                WHERE p.sequence_no = ?
                """, (rs, row) -> new CatalogItemSyncEvent(sequence, token, rs.getInt("attempts"),
                rs.getString("event_id"), rs.getString("event_type"), rs.getInt("event_version"),
                rs.getString("source_service"), rs.getString("saga_id"), rs.getString("correlation_id"),
                rs.getString("causation_id"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getString("business_key"), rs.getString("message_key"), rs.getString("topic"),
                rs.getString("payload"), rs.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                sequence);
        return Optional.of(event);
    }

    boolean canSend(CatalogItemSyncEvent event, UUID activationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM catalog_item_sync_publication p
                    JOIN catalog_item_sync_cutover gate ON gate.id = 1
                    WHERE p.sequence_no = ? AND p.state = 'IN_FLIGHT' AND p.claim_token = ?
                      AND p.available_at > UTC_TIMESTAMP(6) AND gate.activation_id = ? AND gate.send_enabled = TRUE
                      AND gate.mysql_server_uuid = @@GLOBAL.server_uuid AND gate.mysql_schema = DATABASE()
                )
                """, Boolean.class, event.sequence(), event.claimToken(), activationId.toString()));
    }

    boolean sent(CatalogItemSyncEvent event, int partition, long offset) {
        return jdbc.update("""
                UPDATE catalog_item_sync_publication
                SET state = 'SENT', claim_token = NULL, sent_at = UTC_TIMESTAMP(6),
                    broker_partition = ?, broker_offset = ?, last_error = NULL
                WHERE sequence_no = ? AND state = 'IN_FLIGHT' AND claim_token = ?
                """, partition, offset, event.sequence(), event.claimToken()) == 1;
    }

    boolean retry(CatalogItemSyncEvent event, long delayMs, String error) {
        return jdbc.update("""
                UPDATE catalog_item_sync_publication
                SET state = 'RETRY', claim_token = NULL, last_error = ?,
                    available_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                WHERE sequence_no = ? AND state = 'IN_FLIGHT' AND claim_token = ?
                """, error, delayMs * 1000, event.sequence(), event.claimToken()) == 1;
    }

    boolean quarantine(CatalogItemSyncEvent event, String error) {
        return jdbc.update("""
                UPDATE catalog_item_sync_publication SET state = 'QUARANTINED', claim_token = NULL, last_error = ?
                WHERE sequence_no = ? AND state = 'IN_FLIGHT' AND claim_token = ?
                """, error, event.sequence(), event.claimToken()) == 1;
    }
}
