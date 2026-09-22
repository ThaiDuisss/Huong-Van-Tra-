package org.hvtra.commonservice.common_importing.dto.response;

import java.util.List;
import java.util.Map;

public record ImportPreviewItemResponse(
        String id,
        Integer rowIndex,
        Map<String, Object> payload,
        List<ValidationErrorResponse> validationErrors,
        Integer occurrenceCount
) {
}
