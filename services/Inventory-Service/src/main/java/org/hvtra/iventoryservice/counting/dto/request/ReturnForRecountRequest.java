package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Trả kỳ kiểm kê đang chờ duyệt về đếm lại.
 *
 * @param lineIds        các dòng cần đếm lại
 * @param assignedUserId người đếm lại; bỏ trống thì giữ nguyên người đếm đã phân công
 * @param reason         lý do đếm lại
 */
public record ReturnForRecountRequest(
        @NotEmpty List<UUID> lineIds,
        UUID assignedUserId,
        @NotBlank String reason
) {
}
