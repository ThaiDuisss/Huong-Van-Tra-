package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payments extends BaseEntity {

    @Column(name = "payment_code", nullable = false, unique = true, length = 50)
    private String paymentCode;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "amount_required", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRequired;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "amount_refunded", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRefunded;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> transactions = new ArrayList<>();

    public void addTransaction(PaymentTransaction transaction) {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
        transactions.add(transaction);
        transaction.setPayment(this);
    }
}
