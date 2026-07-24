package org.hvtra.deliveryservice.repository;

import org.hvtra.deliveryservice.model.entity.CarrierWebhookEvent;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.UUID;

public interface CarrierWebhookEventRepository extends JpaRepository<CarrierWebhookEvent, UUID> {
    boolean existsByProviderAndDeduplicationKey(DeliveryProvider provider, String deduplicationKey);
}
