package org.hvtra.deliveryservice.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.AuditableEntity;
import org.hvtra.deliveryservice.model.enums.CarrierWebhookStatus;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;


import java.time.Instant;

@Getter
@Entity
@Table(name = "carrier_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_carrier_webhooks_dedup", columnNames = {"provider", "deduplication_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarrierWebhookEvent extends AuditableEntity {

    @Enumerated(EnumType.STRING)

    @Column(name = "provider", nullable = false, length = 50)
    private DeliveryProvider provider;

    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Column(name = "deduplication_key", nullable = false, length = 255)
    private String deduplicationKey;

    @Column(name = "external_order_code", length = 100)
    private String externalOrderCode;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSON")
    private JsonNode rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", columnDefinition = "JSON")
    private JsonNode requestHeaders;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CarrierWebhookStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", foreignKey = @ForeignKey(name = "fk_carrier_webhooks_shipment"))
    private Shipment shipment;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public static CarrierWebhookEvent receive(
            DeliveryProvider provider,
            String providerEventId,
            String deduplicationKey,
            String externalOrderCode,
            String trackingCode,
            String eventType,
            String providerStatus,
            JsonNode payload,
            JsonNode headers,
            Instant receivedAt
    ) {
        CarrierWebhookEvent event = new CarrierWebhookEvent();
        event.provider = provider;
        event.providerEventId = providerEventId;
        event.deduplicationKey = deduplicationKey;
        event.externalOrderCode = externalOrderCode;
        event.trackingCode = trackingCode;
        event.eventType = eventType;
        event.providerStatus = providerStatus;
        event.rawPayload = payload;
        event.requestHeaders = headers;
        event.receivedAt = receivedAt;
        event.status = CarrierWebhookStatus.RECEIVED;
        return event;
    }

    public void bindShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public void markProcessing() {
        if (status != CarrierWebhookStatus.RECEIVED && status != CarrierWebhookStatus.FAILED) {
            throw new IllegalStateException("Webhook cannot be processed in status " + status);
        }
        this.status = CarrierWebhookStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markProcessed(Instant processedAt) {
        this.status = CarrierWebhookStatus.PROCESSED;
        this.processedAt = processedAt;
        this.errorMessage = null;
    }

    public void markIgnored(String reason, Instant processedAt) {
        this.status = CarrierWebhookStatus.IGNORED;
        this.errorMessage = reason;
        this.processedAt = processedAt;
    }

    public void markFailed(String errorMessage) {
        this.status = CarrierWebhookStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
}
