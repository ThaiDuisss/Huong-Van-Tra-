package org.hvtra.deliveryservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.ShipmentStatusHistory;
import java.util.List;
import java.util.UUID;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, UUID> {
    List<ShipmentStatusHistory> findAllByShipmentIdOrderByChangedAtAsc(UUID shipmentId);
}
