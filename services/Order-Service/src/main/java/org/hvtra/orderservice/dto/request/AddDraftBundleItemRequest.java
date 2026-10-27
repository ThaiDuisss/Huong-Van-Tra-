package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Thêm một bundle "Trà đóng theo yêu cầu" vào đơn nháp POS. Không có {@code orderId} thì tạo đơn nháp mới với
 * thông tin quầy/khách như khi thêm mặt hàng thường. Mỗi dòng bundle có số lượng 1.
 */
@Data
public class AddDraftBundleItemRequest {
    private UUID orderId;
    private UUID tenantId;
    private UUID branchId;
    private UUID terminalId;
    private UUID shiftId;
    private UUID cashierId;
    private String cashierNameSnapshot;
    private String branchNameSnapshot;
    private String terminalNameSnapshot;
    private String shiftCodeSnapshot;
    private UUID customerId;
    private String customerNameSnapshot;
    private String customerPhoneSnapshot;
    @Size(max = 255)
    private String bundleName;
    @Size(max = 1000)
    private String note;
    @NotEmpty
    private List<@Valid CustomBundleLineRequest> lines;
}
