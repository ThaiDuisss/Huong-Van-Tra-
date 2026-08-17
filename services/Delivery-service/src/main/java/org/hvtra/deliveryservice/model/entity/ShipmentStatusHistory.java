package org.hvtra.deliveryservice.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.BaseUuidEntity;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;

import java.time.Instant;

@Getter
@Entity
@Table(name = "shipment_status_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentStatusHistory extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shipment_status_histories_shipment"))
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private ShipmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private ShipmentStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private ShipmentStatusSource source;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "changed_by", length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private JsonNode metadata;

    public static ShipmentStatusHistory record(
            Shipment shipment,
            ShipmentStatus previousStatus,
            ShipmentStatus newStatus,
            ShipmentStatusSource source,
            String sourceReference,
            String reasonCode,
            String reason,
            String changedBy,
            Instant changedAt,
            JsonNode metadata
    ) {
        ShipmentStatusHistory history = new ShipmentStatusHistory();
        history.shipment = shipment;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.source = source;
        history.sourceReference = sourceReference;
        history.reasonCode = reasonCode;
        history.reason = reason;
        history.changedBy = changedBy;
        history.changedAt = changedAt;
        history.metadata = metadata;
        return history;
    }
}
