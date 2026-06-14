package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

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
    private String type;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "CHAR(36)")
    private UUID referenceId;
}