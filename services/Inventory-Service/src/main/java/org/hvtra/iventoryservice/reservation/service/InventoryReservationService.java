package org.hvtra.iventoryservice.reservation.service;

import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;

import java.util.List;

public interface InventoryReservationService {
    InventoryReservationResponse apply(ApplyInventoryReservationRequest request);
    InventoryReservationResponse releaseItem(ReleaseInventoryReservationItemRequest request);
    List<InventoryReservationResponse> releaseOrder(ReleaseInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> confirmOrder(ConfirmInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> reserveMaterials(ReserveMaterialOrderRequest request);
    List<InventoryReservationResponse> consumeMaterials(ReleaseInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> releaseMaterials(ReleaseInventoryReservationOrderRequest request);

    /**
     * Trả lại nguyên liệu đã giữ chỗ khi bán (để sản xuất bù) cho lệnh sản xuất dùng: giải phóng các giữ chỗ
     * cũ nhất của mặt hàng cho tới khi đủ {@code quantity}. Trả về tổng số lượng đã giải phóng.
     */
    java.math.BigDecimal releasePlannedMaterials(java.util.UUID productVariantId, java.util.UUID inventoryItemId,
            java.math.BigDecimal quantity);
    int expireReservations();
    List<InventoryReservationResponse> getExpiringReservations(int withinMinutes);
}
