package org.hvtra.deliveryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.DeliveryProof;
import java.util.List;
import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {
    List<DeliveryProof> findAllByShipmentId(UUID shipmentId);
}
