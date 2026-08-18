package org.hvtra.iventoryservice.reservation.service;

import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;

import java.util.List;

public interface InventoryReservationService {
    InventoryReservationResponse apply(ApplyInventoryReservationRequest request);
    InventoryReservationResponse releaseItem(ReleaseInventoryReservationItemRequest request);
    List<InventoryReservationResponse> releaseOrder(ReleaseInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> confirmOrder(ConfirmInventoryReservationOrderRequest request);
    int expireReservations();
}
