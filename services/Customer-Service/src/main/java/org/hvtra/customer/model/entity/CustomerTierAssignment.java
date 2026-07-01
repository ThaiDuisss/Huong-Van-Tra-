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
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_tier_assignments")
public class CustomerTierAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private CustomerTier tier;

    @Column(name = "qualification_year")
    private Integer qualificationYear;

    @Column(name = "assignment_reason", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerTierAssignmentReason assignmentReason;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerTierAssignmentStatus status = CustomerTierAssignmentStatus.ACTIVE;

    @Column(name = "note", length = 500)
    private String note;
}
