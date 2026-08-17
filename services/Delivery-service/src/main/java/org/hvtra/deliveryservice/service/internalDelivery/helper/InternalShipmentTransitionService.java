package org.hvtra.deliveryservice.service.internalDelivery.helper;

import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentStatusHistory;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.repository.ShipmentStatusHistoryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InternalShipmentTransitionService {

    private final ShipmentStatusHistoryRepository historyRepository;

    public InternalShipmentTransitionService(ShipmentStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void transition(
            Shipment shipment,
            Runnable action,
            ShipmentStatusSource source,
            String sourceReference,
            String reasonCode,
            String reason,
            String changedBy
    ) {
        ShipmentStatus previous = shipment.getStatus();
        Instant changedAt = Instant.now();
        action.run();
        if (previous == shipment.getStatus()) {
            return;
        }
        historyRepository.save(ShipmentStatusHistory.record(
                shipment,
                previous,
                shipment.getStatus(),
                source,
                sourceReference,
                reasonCode,
                reason,
                changedBy,
                changedAt,
                null
        ));
    }
}
