package org.hvtra.iventoryservice.reservation.service;

import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationService {
    InventoryReservationResponse apply(ApplyInventoryReservationRequest request);
    InventoryReservationResponse releaseItem(ReleaseInventoryReservationItemRequest request);
    List<InventoryReservationResponse> releaseOrder(ReleaseInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> confirmOrder(ConfirmInventoryReservationOrderRequest request);
    List<InventoryReservationResponse> reserveMaterials(ReserveMaterialOrderRequest request);
    List<InventoryReservationResponse> consumeMaterials(ReleaseInventoryReservationOrderRequest request);

    /**
     * Trừ nguyên liệu đã giữ chỗ theo khóa {@code reservationKey} và ghi giao dịch theo loại/chứng từ do nơi gọi
     * chỉ định (vd đóng trà theo yêu cầu: CUSTOM_BUNDLE_ISSUE) thay vì SALE_OUT theo đơn.
     */
    List<InventoryReservationResponse> consumeMaterials(UUID reservationKey, InventoryTransactionType type,
            InventoryReferenceType referenceType, String referenceId, String transactionCode);
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
