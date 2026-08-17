package org.hvtra.deliveryservice.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.AuditableEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;


import java.time.Instant;

@Getter
@Entity
@Table(name = "carrier_shipments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_carrier_shipments_shipment_provider", columnNames = {"shipment_id", "provider"}),
                @UniqueConstraint(name = "uk_carrier_shipments_client_order", columnNames = {"provider", "client_order_code"}),
                @UniqueConstraint(name = "uk_carrier_shipments_external_order", columnNames = {"provider", "external_order_code"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarrierShipment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_carrier_shipments_shipment"))
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private DeliveryProvider provider;

    @Column(name = "shop_id", length = 100)
    private String shopId;

    @Column(name = "service_code", length = 100)
    private String serviceCode;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "client_order_code", nullable = false, length = 100)
    private String clientOrderCode;

    @Column(name = "external_order_code", length = 100)
    private String externalOrderCode;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "tracking_url", length = 1000)
    private String trackingUrl;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "shipping_fee")
    private Long shippingFee;

    @Column(name = "insurance_fee")
    private Long insuranceFee;

    @Column(name = "cod_fee")
    private Long codFee;

    @Column(name = "total_fee")
    private Long totalFee;

    @Column(name = "cod_amount", nullable = false)
    private long codAmount;

    @Column(name = "cod_collected", nullable = false)
    private boolean codCollected;

    @Column(name = "cod_remitted", nullable = false)
    private boolean codRemitted;

    @Column(name = "cod_remitted_at")
    private Instant codRemittedAt;

    @Column(name = "expected_delivery_from")
    private Instant expectedDeliveryFrom;

    @Column(name = "expected_delivery_to")
    private Instant expectedDeliveryTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "create_request", columnDefinition = "JSON")
    private JsonNode createRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "create_response", columnDefinition = "JSON")
    private JsonNode createResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_metadata", columnDefinition = "JSON")
    private JsonNode providerMetadata;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    public static CarrierShipment create(
            Shipment shipment,
            DeliveryProvider provider,
            String clientOrderCode,
            long codAmount,
            JsonNode createRequest
    ) {
        if (provider == DeliveryProvider.STORE_COURIER) {
            throw new IllegalArgumentException("CarrierShipment is only for external providers");
        }
        CarrierShipment carrier = new CarrierShipment();
        carrier.shipment = shipment;
        carrier.provider = provider;
        carrier.clientOrderCode = clientOrderCode;
        carrier.codAmount = codAmount;
        carrier.createRequest = createRequest;
        return carrier;
    }

    public void recordCreatedShipment(
            String shopId,
            String serviceCode,
            String serviceName,
            String externalOrderCode,
            String trackingCode,
            String providerStatus,
            Long shippingFee,
            Long insuranceFee,
            Long codFee,
            Long totalFee,
            Instant expectedFrom,
            Instant expectedTo,
            JsonNode createResponse
    ) {
        this.shopId = shopId;
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.externalOrderCode = externalOrderCode;
        this.trackingCode = trackingCode;
        this.providerStatus = providerStatus;
        this.shippingFee = shippingFee;
        this.insuranceFee = insuranceFee;
        this.codFee = codFee;
        this.totalFee = totalFee;
        this.expectedDeliveryFrom = expectedFrom;
        this.expectedDeliveryTo = expectedTo;
        this.createResponse = createResponse;
    }

    public void updateCreateRequest(JsonNode createRequest) {
        if (createRequest != null) {
            this.createRequest = createRequest;
        }
    }
    public void updateProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public void updateTrackingUrl(String trackingUrl) {
        if (trackingUrl != null && !trackingUrl.isBlank()) {
            this.trackingUrl = trackingUrl;
        }
    }

    public void updateProviderMetadata(JsonNode providerMetadata) {
        if (providerMetadata != null && !providerMetadata.isNull()) {
            this.providerMetadata = providerMetadata;
        }
    }

    public void markSynced(Instant syncedAt) {
        this.lastSyncedAt = syncedAt == null ? Instant.now() : syncedAt;
    }


    public void updateFees(Long shippingFee, Long insuranceFee, Long codFee, Long totalFee) {
        if (shippingFee != null && shippingFee >= 0) {
            this.shippingFee = shippingFee;
        }
        if (insuranceFee != null && insuranceFee >= 0) {
            this.insuranceFee = insuranceFee;
        }
        if (codFee != null && codFee >= 0) {
            this.codFee = codFee;
        }
        if (totalFee != null && totalFee >= 0) {
            this.totalFee = totalFee;
        }
    }
    public void updateCodAmount(Long codAmount) {
        if (codAmount != null && codAmount >= 0) {
            this.codAmount = codAmount;
        }
    }

    public void markCodCollected() {
        this.codCollected = true;
    }

    public void markCodRemitted(Instant remittedAt) {
        if (!codCollected) {
            throw new IllegalStateException("COD cannot be remitted before being collected");
        }
        this.codRemitted = true;
        this.codRemittedAt = remittedAt;
    }
}
