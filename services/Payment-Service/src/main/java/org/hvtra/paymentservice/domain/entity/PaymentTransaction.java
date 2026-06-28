package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, columnDefinition = "BINARY(16)")
    private Payments payment;

    @Column(name = "method", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;
}
