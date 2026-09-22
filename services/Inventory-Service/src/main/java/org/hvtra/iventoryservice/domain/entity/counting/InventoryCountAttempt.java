package org.hvtra.iventoryservice.domain.entity.counting;

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
import org.hvtra.iventoryservice.domain.enums.InventoryCountAttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_attempts")
@Getter
@Setter
public class InventoryCountAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "count_line_id", nullable = false)
    private InventoryCountLine countLine;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "counter_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID counterId;

    @Column(name = "counted_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal countedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InventoryCountAttemptStatus status = InventoryCountAttemptStatus.SUBMITTED;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();
}
