package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.BaseUuidEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryProofType;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "delivery_proofs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryProof extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_delivery_proofs_shipment"))
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_attempt_id", foreignKey = @ForeignKey(name = "fk_delivery_proofs_attempt"))
    private DeliveryAttempt deliveryAttempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 30)
    private DeliveryProofType proofType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "file_id", columnDefinition = "BINARY(16)")
    private UUID fileId;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified;

    @Column(name = "receiver_name", length = 255)
    private String receiverName;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DeliveryProof create(
            Shipment shipment,
            DeliveryAttempt attempt,
            DeliveryProofType type,
            UUID fileId,
            boolean otpVerified,
            String receiverName,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant capturedAt
    ) {
        if ((type == DeliveryProofType.PHOTO || type == DeliveryProofType.SIGNATURE) && fileId == null) {
            throw new IllegalArgumentException("fileId is required for photo or signature proof");
        }
        DeliveryProof proof = new DeliveryProof();
        proof.shipment = shipment;
        proof.deliveryAttempt = attempt;
        proof.proofType = type;
        proof.fileId = fileId;
        proof.otpVerified = otpVerified;
        proof.receiverName = receiverName;
        proof.latitude = latitude;
        proof.longitude = longitude;
        proof.capturedAt = capturedAt;
        return proof;
    }
}
