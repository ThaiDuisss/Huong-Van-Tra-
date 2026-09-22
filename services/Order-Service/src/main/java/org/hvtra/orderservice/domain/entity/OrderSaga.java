package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hvtra.commonservice.common_core.kafka.saga.domain.BaseSaga;

import java.util.UUID;


@Entity
@Table(
        name = "order_saga",
        indexes = {
                @Index(name = "idx_order_saga_saga_id", columnList = "saga_id"),
                @Index(name = "idx_order_saga_order_id", columnList = "order_id"),
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

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID orderId;

    /**
     * Field riêng của Order Service nếu cần.
     */
    String orderCode;
}
