package org.hvtra.deliveryservice.service;

import org.hvtra.deliveryservice.carrier.support.ShipmentStatusTransitionPolicy;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.service.ghn.GhnStatusMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhnStatusMapperTest {

    private final GhnStatusMapper mapper = new GhnStatusMapper(new ShipmentStatusTransitionPolicy());

    @Test
    void mapsGhnStatusesToInternalStatuses() {
        assertThat(mapper.toShipmentStatus("ready_to_pick")).isEqualTo(ShipmentStatus.READY_FOR_PICKUP);
        assertThat(mapper.toShipmentStatus("picked")).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(mapper.toShipmentStatus("delivering")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(mapper.toShipmentStatus("delivered")).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(mapper.toShipmentStatus("delivery_fail")).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
        assertThat(mapper.toShipmentStatus("returning")).isEqualTo(ShipmentStatus.RETURNING);
        assertThat(mapper.toShipmentStatus("returned")).isEqualTo(ShipmentStatus.RETURNED);
        assertThat(mapper.toShipmentStatus("cancel")).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void ignoresOutOfOrderStatusRegression() {
        assertThat(mapper.canApply(ShipmentStatus.IN_TRANSIT, ShipmentStatus.READY_FOR_PICKUP)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.IN_TRANSIT, ShipmentStatus.PICKED_UP)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.READY_FOR_PICKUP)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.RETURNING, ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(mapper.canApply(ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED)).isFalse();
    }

    @Test
    void allowsExpectedForwardTransitions() {
        assertThat(mapper.canApply(ShipmentStatus.READY_FOR_PICKUP, ShipmentStatus.PICKED_UP)).isTrue();
        assertThat(mapper.canApply(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(mapper.canApply(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED)).isTrue();
        assertThat(mapper.canApply(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURN_REQUESTED)).isTrue();
        assertThat(mapper.canApply(ShipmentStatus.RETURN_REQUESTED, ShipmentStatus.RETURNING)).isTrue();
    }
}
