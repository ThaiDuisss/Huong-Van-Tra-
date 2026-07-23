package org.hvtra.deliveryservice.service.internalDelivery.helper;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.request.internal.ConfigureTenantDeliveryRequest;
import org.hvtra.deliveryservice.model.response.internal.TenantDeliveryConfigResponse;
import org.hvtra.deliveryservice.repository.TenantDeliveryConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantDeliveryConfigService {

    private final TenantDeliveryConfigRepository repository;
    private final InternalDeliveryResponseMapper responseMapper;


    @Transactional
    public TenantDeliveryConfigResponse configure(
            UUID tenantId,
            ConfigureTenantDeliveryRequest request
    ) {
        TenantDeliveryConfig config = repository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> TenantDeliveryConfig.create(tenantId));
        config.configureInternalDelivery(
                request.internalDeliveryEnabled(),
                request.maxDeliveryDistanceKm(),
                request.maxRouteWeightGram(),
                request.maxRouteCodAmount(),
                request.maxRouteDurationMin(),
                request.maxOrdersPerRoute(),
                request.baseShippingFee(),
                request.feePerKm(),
                request.fallbackProvider()
        );
        config.configureCutoffTimes(
                request.morningCutoffTime(),
                request.afternoonCutoffTime(),
                request.eveningCutoffTime()
        );
        config.configurePlanning(
                request.planningAlgorithm(),
                request.averageSpeedKmh(),
                request.travelEstimationMode(),
                request.serviceTimePerStopMin()
        );
        if (Boolean.FALSE.equals(request.active())) {
            config.deactivate();
        } else {
            config.activate();
        }
        return responseMapper.toConfigResponse(repository.save(config));
    }

    @Transactional(readOnly = true)
    public TenantDeliveryConfigResponse get(UUID tenantId) {
        TenantDeliveryConfig config = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant delivery config not found: " + tenantId
                ));
        return responseMapper.toConfigResponse(config);
    }
}
