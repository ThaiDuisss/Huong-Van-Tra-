package org.hvtra.orderservice.service;

import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.dto.request.AddDraftBundleItemRequest;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftBundleItemRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** Trà đóng theo yêu cầu (CUSTOM_BUNDLE) trên đơn bán POS. */
public interface OrderCustomBundleService {

    OrderResponse addDraftBundleItem(AddDraftBundleItemRequest request);

    OrderResponse updateDraftBundleItem(UUID orderId, UUID itemId, UpdateDraftBundleItemRequest request);

    /** Đơn đã thu tiền có dòng bundle, chưa giao: lọc theo trạng thái đóng gói nếu có. */
    Page<OrderResponse> getAwaitingHandover(String keyword, BundlePackingStatus packingStatus, int page, int size);

    /** Tạo yêu cầu đóng gói còn thiếu và lấy lại trạng thái từ Inventory. */
    OrderResponse refreshPacking(UUID orderId);

    /**
     * Giao bundle cho khách khi mọi dòng bundle đã đóng gói. Đơn chờ hàng (đặt cọc / chờ nguyên liệu) đi qua
     * luồng giao đơn chờ để trừ tồn hàng thường và thu phần còn lại.
     */
    OrderResponse handover(UUID orderId, FulfillBackorderRequest request);

    /** Kafka CUSTOM_BUNDLE_PACKED. @return false nếu không tìm thấy dòng hoặc đã cập nhật trước đó. */
    boolean markPacked(UUID orderId, UUID orderItemId, String packingCode);
}
