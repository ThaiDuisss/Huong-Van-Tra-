package org.hvtra.deliveryservice.service;

import org.hvtra.deliveryservice.carrier.support.ShipmentStatusTransitionPolicy;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.response.ahmove.AhamovePathResponse;
import org.hvtra.deliveryservice.service.ahmove.AhamoveStatusMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
class AhamoveStatusMapperTest {

    private final AhamoveStatusMapper mapper = new AhamoveStatusMapper(new ShipmentStatusTransitionPolicy());

    @Test
    void mapsLiveOrderStatuses() {
        assertThat(mapper.toShipmentStatus("ASSIGNING", null, List.of()))
                .isEqualTo(ShipmentStatus.READY_FOR_PICKUP);
        assertThat(mapper.toShipmentStatus("IN PROCESS", null, List.of()))
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(mapper.toShipmentStatus("CANCELLED", null, List.of()))
                .isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void completedOrderIsDeliveredOnlyWhenDeliveryStopCompleted() {
        assertThat(mapper.toShipmentStatus("COMPLETED", null, List.of(pickup(), delivery("COMPLETED"))))
                .isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(mapper.toShipmentStatus("COMPLETED", null, List.of(pickup(), delivery(""))))
                .isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void mapsFailedAndReturnFlowFromDeliveryStop() {
        assertThat(mapper.toShipmentStatus("COMPLETED", null, List.of(pickup(), delivery("FAILED"))))
                .isEqualTo(ShipmentStatus.DELIVERY_FAILED);
        assertThat(mapper.toShipmentStatus("COMPLETED", "IN_RETURN", List.of(pickup(), delivery("FAILED"))))
                .isEqualTo(ShipmentStatus.RETURNING);
        assertThat(mapper.toShipmentStatus("COMPLETED", "RETURNED", List.of(pickup(), delivery("FAILED"))))
                .isEqualTo(ShipmentStatus.RETURNED);
    }

    @Test
    void sharesTheSameRegressionPolicyAsOtherCarriers() {
        assertThat(mapper.canApply(ShipmentStatus.IN_TRANSIT, ShipmentStatus.READY_FOR_PICKUP)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.DELIVERED, ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNING)).isTrue();
    }

    private AhamovePathResponse pickup() {
        return new AhamovePathResponse(
                "Pickup", "0900000000", "Store", null, null, null,
                "COMPLETED", null, null, null, null, null, null
        );
    }

    private AhamovePathResponse delivery(String status) {
        return new AhamovePathResponse(
                "Drop", "0911111111", "Customer", 100_000L, 200_000L, "SHP-001",
                status, null, null, null, null, null, null
        );
    }
}
