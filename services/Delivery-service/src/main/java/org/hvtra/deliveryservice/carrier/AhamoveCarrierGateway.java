package org.hvtra.deliveryservice.carrier;

import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.service.ahmove.AhamoveIntegrationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AhamoveCarrierGateway implements CarrierGateWay {

    private final AhamoveIntegrationService service;

    public AhamoveCarrierGateway(AhamoveIntegrationService service) {
        this.service = service;
    }

    @Override
    public DeliveryProvider provider() {
        return DeliveryProvider.AHAMOVE;
    }

    @Override
    public CarrierCapabilities capabilities() {
        return new CarrierCapabilities(
                provider(),
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                true,
                true
        );
    }

    @Override
    public List<CarrierQuoteResult> quote(UUID shipmentId, CarrierQuoteCommand command) {
        return service.quote(shipmentId, command);
    }

    @Override
    public CarrierShipmentResult create(UUID shipmentId, CarrierCreateCommand command) {
        return service.create(shipmentId, command);
    }

    @Override
    public CarrierShipmentResult refresh(UUID shipmentId) {
        return service.refresh(shipmentId);
    }

    @Override
    public CarrierShipmentResult cancel(UUID shipmentId, CarrierCancelCommand command) {
        return service.cancel(shipmentId, command);
    }

    @Override
    public CarrierShipmentResult get(UUID shipmentId) {
        return service.get(shipmentId);
    }
}
