package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hvtra.commonservice.kafka.saga.domain.BaseSaga;


@Entity
@Table(
        name = "order_saga",
        indexes = {
                @Index(name = "idx_order_saga_saga_id", columnList = "saga_id"),
                @Index(name = "idx_order_saga_entity_id", columnList = "entity_id"),
                @Index(name = "idx_order_saga_status", columnList = "status")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSaga extends BaseSaga {

    /**
     * Field riêng của Order Service nếu cần.
     */
    String orderCode;
}