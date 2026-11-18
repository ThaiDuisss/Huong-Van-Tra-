package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;

import java.util.UUID;

/** Một dòng trong sổ cái điểm của khách. Số dư hiện tại nằm ở {@link Customer#getPointBalance()}. */
@Getter
@Setter
@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LoyaltyTransactionType type;

    /** Số điểm có dấu: dương là cộng, âm là trừ. */
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "reference_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LoyaltyReferenceType referenceType;

    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    private UUID referenceId;

    @Column(name = "reference_code", length = 50)
    private String referenceCode;

    /** Đơn gốc của giao dịch EARN / REVERSE. */
    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "reason", length = 500)
    private String reason;
}
