package org.hvtra.iventoryservice.domain.entity.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "production_order_sequences")
@Getter
@Setter
public class ProductionOrderSequence {

    @Id
    @Column(name = "sequence_key", length = 50)
    private String sequenceKey;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
