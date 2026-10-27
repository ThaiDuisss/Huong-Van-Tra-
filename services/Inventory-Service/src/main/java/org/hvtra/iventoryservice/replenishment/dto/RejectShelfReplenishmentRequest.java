package org.hvtra.iventoryservice.replenishment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do từ chối phiếu nhập kệ — bắt buộc để người tạo biết cần sửa gì. */
public record RejectShelfReplenishmentRequest(
        @NotBlank(message = "Vui lòng nhập lý do từ chối")
        @Size(max = 1000, message = "Lý do từ chối tối đa 1000 ký tự")
        String reason
) {
}
