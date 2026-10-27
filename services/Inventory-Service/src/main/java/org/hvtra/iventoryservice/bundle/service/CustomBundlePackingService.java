package org.hvtra.iventoryservice.bundle.service;

import org.hvtra.iventoryservice.bundle.dto.request.CancelCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.ConfirmCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CreateCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.domain.enums.CustomBundlePackingStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomBundlePackingService {

    /**
     * Tạo yêu cầu đóng gói cho dòng bundle đã thu tiền. Đủ nguyên liệu thì giữ chỗ ngay ({@code AWAITING_PACKING});
     * thiếu thì {@code WAITING_MATERIAL} kèm ngày dự kiến và mở cảnh báo nhập nguyên liệu. Idempotent theo orderItemId.
     */
    CustomBundlePackingResponse create(CreateCustomBundlePackingRequest request);

    /** Đơn đang chờ nguyên liệu: kiểm tra lại, đủ thì giữ chỗ và chuyển sang chờ đóng gói. */
    CustomBundlePackingResponse recheck(UUID id);

    /** Nhân viên kho xác nhận đã đóng gói: trừ nguyên liệu đã giữ (CUSTOM_BUNDLE_ISSUE) và báo Order. */
    CustomBundlePackingResponse confirm(UUID id, ConfirmCustomBundlePackingRequest request);

    /** Huỷ các yêu cầu chưa đóng gói của đơn, trả lại nguyên liệu đã giữ. */
    List<CustomBundlePackingResponse> cancelByOrder(CancelCustomBundlePackingRequest request);

    PageResponse<CustomBundlePackingResponse> findAll(
            CustomBundlePackingStatus status, UUID branchId, String search, Pageable pageable);

    CustomBundlePackingResponse findById(UUID id);

    List<CustomBundlePackingResponse> findByOrder(UUID orderId);
}
