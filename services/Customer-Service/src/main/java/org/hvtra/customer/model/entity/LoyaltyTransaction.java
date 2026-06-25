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
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_account_id", nullable = false)
    private LoyaltyAccount loyaltyAccount;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LoyaltyTransactionType type;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "reference_type", length = 50)
    @Enumerated(EnumType.STRING)
    private LoyaltyReferenceType referenceType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    private UUID referenceId;
}
