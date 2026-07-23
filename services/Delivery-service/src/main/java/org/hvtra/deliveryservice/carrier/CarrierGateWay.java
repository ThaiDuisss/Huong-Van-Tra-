package org.hvtra.deliveryservice.carrier;

import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;

import java.util.List;
import java.util.UUID;

public interface CarrierGateWay {
    DeliveryProvider provider();

    CarrierCapabilities capabilities();

    List<CarrierQuoteResult> quote(UUID shipmentId, CarrierQuoteCommand command);

    CarrierShipmentResult create(UUID shipmentId, CarrierCreateCommand command);

    CarrierShipmentResult refresh(UUID shipmentId);

    CarrierShipmentResult cancel(UUID shipmentId, CarrierCancelCommand command);

    CarrierShipmentResult get(UUID shipmentId);
}
