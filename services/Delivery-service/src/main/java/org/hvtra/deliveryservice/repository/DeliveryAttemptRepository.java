package org.hvtra.deliveryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.DeliveryAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findAllByShipmentIdOrderByAttemptNoAsc(UUID shipmentId);
    Optional<DeliveryAttempt> findTopByShipmentIdOrderByAttemptNoDesc(UUID shipmentId);
}
