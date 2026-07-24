package org.hvtra.deliveryservice.model.entity;

import org.hvtra.deliveryservice.model.enums.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRouteLifecycleTest {

    @Test
    void completesRouteAfterAllStopsBecomeTerminal() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        Shipment delivered = shipment(tenantId,  "SHP-001", 1_000, 200_000);
        Shipment failed = shipment(tenantId, "SHP-002", 2_000, 300_000);
        delivered.markReadyForPlanning();
        failed.markReadyForPlanning();
        delivered.markPlanned();
        failed.markPlanned();

        Instant startAt = Instant.parse("2026-07-20T01:00:00Z");
        DeliveryRoute route = DeliveryRoute.create(
                tenantId,
                "IR-260720010000-ABCDEF12",
                DispatchWave.MORNING
        );
        route.plan(startAt, startAt.plusSeconds(3_600), "NEAREST_NEIGHBOR");
        RouteStop first = RouteStop.delivery(delivered, 1, startAt.plusSeconds(600));
        RouteStop second = RouteStop.delivery(failed, 2, startAt.plusSeconds(1_200));
        route.addStop(first);
        route.addStop(second);
        route.markReady();
        route.assignCourier(courierId, "Courier A");
        delivered.assignCourier(courierId, "Courier A");
        failed.assignCourier(courierId, "Courier A");
        route.start(startAt);
        startShipment(delivered, startAt);
        startShipment(failed, startAt);

        first.markArriving();
        first.markDelivered(startAt.plusSeconds(900));
        delivered.markDelivered(startAt.plusSeconds(900), 200_000);
        second.markFailed("CUSTOMER_UNAVAILABLE", "Customer was not home", startAt.plusSeconds(1_500));
        failed.markDeliveryFailed("CUSTOMER_UNAVAILABLE", "Customer was not home");
        route.complete(startAt.plusSeconds(1_500));

        assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.COMPLETED);
        assertThat(route.getTotalStops()).isEqualTo(2);
        assertThat(route.getTotalWeightGram()).isEqualTo(3_000);
        assertThat(route.getTotalCodAmount()).isEqualTo(500_000);
        assertThat(first.getStatus()).isEqualTo(RouteStopStatus.DELIVERED);
        assertThat(second.getStatus()).isEqualTo(RouteStopStatus.FAILED);
        assertThat(delivered.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(failed.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
    }

    private void startShipment(Shipment shipment, Instant startedAt) {
        shipment.markReadyForPickup();
        shipment.markPickedUp(startedAt);
        shipment.markInTransit();
    }

    private Shipment shipment(
            UUID tenantId,
            String code,
            int weightGram,
            long codAmount
    ) {
        Shipment shipment = Shipment.create(
                tenantId,
                code,
                UUID.randomUUID(),
                "ORDER-" + code,
                1,
                DeliveryMode.INTERNAL,
                DeliveryProvider.STORE_COURIER,
                PaymentType.COD,
                codAmount,
                0,
                UUID.randomUUID(),
                "Receiver",
                "0900000000",
                "Delivery address",
                "Pickup address"
        );
        shipment.configurePackage(1, weightGram, 10, 10, 10, codAmount);
        return shipment;
    }
}
