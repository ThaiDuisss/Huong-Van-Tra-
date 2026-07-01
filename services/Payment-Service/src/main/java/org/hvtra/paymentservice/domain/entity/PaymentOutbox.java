package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;

@Entity
@Table(
        name = "payment_outbox",
        indexes = {
                @Index(name = "idx_payment_outbox_status", columnList = "status"),
                @Index(name = "idx_payment_outbox_event_id", columnList = "event_id"),
                @Index(name = "idx_payment_outbox_saga_id", columnList = "saga_id"),
                @Index(name = "idx_payment_outbox_entity_id", columnList = "entity_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOutbox extends BaseOutboxEvent {

    /**
     * Nếu payment service muốn thêm field riêng thì thêm ở đây.
     * Common không cần biết.
     */
    String paymentProvider;
}