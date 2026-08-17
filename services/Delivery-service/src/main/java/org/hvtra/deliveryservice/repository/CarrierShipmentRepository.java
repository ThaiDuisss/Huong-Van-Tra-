package org.hvtra.deliveryservice.repository;

import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;

public interface CarrierShipmentRepository extends JpaRepository<CarrierShipment, UUID> {
    Optional<CarrierShipment> findByProviderAndTrackingCode(DeliveryProvider provider, String trackingCode);
    Optional<CarrierShipment> findByProviderAndExternalOrderCode(DeliveryProvider provider, String externalOrderCode);
    Optional<CarrierShipment> findByShipmentIdAndProvider(UUID shipmentId, DeliveryProvider provider);
}
