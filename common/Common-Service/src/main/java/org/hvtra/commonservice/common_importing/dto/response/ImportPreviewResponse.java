package org.hvtra.commonservice.common_importing.dto.response;

import org.hvtra.commonservice.common_importing.enums.ImportMode;
import org.hvtra.commonservice.common_importing.enums.ImportType;

import java.util.List;

public record ImportPreviewResponse(
        ImportType importType,
        ImportMode importMode,
        String sourceFileName,
        int totalRows,
        int validRows,
        int errorRows,
        List<ImportPreviewItemResponse> items
) {
}
