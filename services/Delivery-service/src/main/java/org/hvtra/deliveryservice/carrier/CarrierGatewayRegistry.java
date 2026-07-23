package org.hvtra.deliveryservice.carrier;

import org.hvtra.deliveryservice.carrier.model.CarrierCapabilities;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CarrierGatewayRegistry {

    private final Map<DeliveryProvider, CarrierGateWay> gateways;

    public CarrierGatewayRegistry(List<CarrierGateWay> gatewayList) {
        EnumMap<DeliveryProvider, CarrierGateWay> registry = new EnumMap<>(DeliveryProvider.class);
        for (CarrierGateWay gateway : gatewayList) {
            CarrierGateWay previous = registry.put(gateway.provider(), gateway);
            if (previous != null) {
                throw new IllegalStateException("Duplicate carrier gateway for " + gateway.provider());
            }
        }
        this.gateways = Map.copyOf(registry);
    }

    public Set<DeliveryProvider> supportedProviders() {
        return gateways.keySet();
    }

    public CarrierCapabilities capabilities(DeliveryProvider provider) {
        return require(provider).capabilities();
    }

    public CarrierGateWay require(DeliveryProvider provider) {
        CarrierGateWay gateway = gateways.get(provider);
        if (gateway == null) {
            throw new IllegalArgumentException("External carrier is not supported: " + provider);
        }
        return gateway;
    }
}
