package org.hvtra.iventoryservice.document.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.domain.enums.ReceiptExcessResolution;
import org.hvtra.iventoryservice.domain.enums.ReceiptShortageResolution;

import java.util.List;
import java.util.UUID;

/**
 * Cách xử lý ngoại lệ cho từng dòng khi duyệt ngoại lệ & ghi sổ phiếu nhập.
 * Dòng giao thừa bắt buộc có {@code excessResolution}, dòng giao thiếu bắt buộc có {@code shortageResolution};
 * phần không đạt QC luôn được hủy nên không cần chọn.
 */
public record ResolveReceiptExceptionRequest(
        @Valid
        List<LineResolution> lines,

        /** Ghi chú duyệt ngoại lệ của quản lý cho cả phiếu. */
        @Size(max = 2000, message = "Exception approval note must be at most 2000 characters")
        String approvalNote
) {
    public record LineResolution(
            @NotNull(message = "Line ID is required")
            UUID lineId,

            ReceiptExcessResolution excessResolution,

            ReceiptShortageResolution shortageResolution,

            @Size(max = 500, message = "Resolution note must be at most 500 characters")
            String note
    ) {
    }
}
