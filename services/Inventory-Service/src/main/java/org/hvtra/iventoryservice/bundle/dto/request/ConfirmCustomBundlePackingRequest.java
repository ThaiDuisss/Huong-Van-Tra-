package org.hvtra.iventoryservice.bundle.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param pickedLineIds      các dòng nguyên liệu đã lấy; phải gồm đủ mọi dòng của yêu cầu (v1 đóng đúng số lượng
 *                           đã giữ, không đóng thiếu)
 * @param actualTotalWeight  khối lượng thực sau khi đóng (kể cả bao bì), chỉ để đối chiếu
 */
public record ConfirmCustomBundlePackingRequest(
        @NotEmpty List<@NotNull UUID> pickedLineIds,
        @Size(max = 100) String packagingType,
        @Positive Integer numberOfPackages,
        @DecimalMin(value = "0", inclusive = false) BigDecimal actualTotalWeight,
        @Size(max = 1000) String note
) {
}
