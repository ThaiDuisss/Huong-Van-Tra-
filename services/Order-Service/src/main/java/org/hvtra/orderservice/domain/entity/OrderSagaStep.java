package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hvtra.commonservice.kafka.saga.domain.BaseSagaStep;

@Entity
@Table(
        name = "order_saga_step",
        indexes = {
                @Index(name = "idx_order_saga_step_saga_id", columnList = "saga_id"),
                @Index(name = "idx_order_saga_step_status", columnList = "status"),
                @Index(name = "idx_order_saga_step_name", columnList = "step_name")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSagaStep extends BaseSagaStep {
}