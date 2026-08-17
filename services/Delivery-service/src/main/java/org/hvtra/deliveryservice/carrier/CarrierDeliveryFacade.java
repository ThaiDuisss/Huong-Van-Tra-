package org.hvtra.deliveryservice.carrier;

import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CarrierDeliveryFacade {

    private final CarrierGatewayRegistry registry;

    public CarrierDeliveryFacade(CarrierGatewayRegistry registry) {
        this.registry = registry;
    }

    public List<CarrierQuoteResult> quote(UUID shipmentId, DeliveryProvider provider, CarrierQuoteCommand command) {
        return registry.require(provider).quote(shipmentId, command);
    }

    public CarrierShipmentResult create(UUID shipmentId, DeliveryProvider provider, CarrierCreateCommand command) {
        return registry.require(provider).create(shipmentId, command);
    }

    public CarrierShipmentResult refresh(UUID shipmentId, DeliveryProvider provider) {
        return registry.require(provider).refresh(shipmentId);
    }

    public CarrierShipmentResult cancel(UUID shipmentId, DeliveryProvider provider, CarrierCancelCommand command) {
        return registry.require(provider).cancel(shipmentId, command == null ? new CarrierCancelCommand(null) : command);
    }

    public CarrierShipmentResult get(UUID shipmentId, DeliveryProvider provider) {
        return registry.require(provider).get(shipmentId);
    }
}
