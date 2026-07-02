package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.CashCollectionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "cash_collections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cash_collection_delivery_id", columnNames = "delivery_id")
        },
        indexes = {
                @Index(name = "idx_cash_collections_payment_id", columnList = "payment_id"),
                @Index(name = "idx_cash_collections_order_id", columnList = "order_id"),
                @Index(name = "idx_cash_collections_status", columnList = "status"),
                @Index(name = "idx_cash_collections_collector", columnList = "collector_user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CashCollection {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cash_collections_payment")
    )
    Payment payment;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID orderId;

    @Column(name = "delivery_id", columnDefinition = "BINARY(16)")
    UUID deliveryId;

    @Column(name = "collector_user_id", columnDefinition = "BINARY(16)")
    UUID collectorUserId;

    @Column(name = "amount_expected", nullable = false, precision = 14, scale = 2)
    BigDecimal amountExpected;

    @Column(name = "amount_collected", precision = 14, scale = 2)
    BigDecimal amountCollected;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    CashCollectionStatus status;

    @Column(name = "assigned_at")
    LocalDateTime assignedAt;

    @Column(name = "collected_at")
    LocalDateTime collectedAt;

    @Column(name = "reconciled_at")
    LocalDateTime reconciledAt;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = CashCollectionStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}