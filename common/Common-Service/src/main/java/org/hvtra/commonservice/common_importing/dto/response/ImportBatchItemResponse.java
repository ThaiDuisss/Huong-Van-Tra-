package org.hvtra.commonservice.importing.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ImportBatchItemResponse(
        UUID id,
        Integer rowIndex,
        Map<String, Object> payload,
        List<ValidationErrorResponse> validationErrors,
        Integer occurrenceCount,
        UUID createdEntityId
) {
    public boolean isValid() {
        return validationErrors == null || validationErrors.isEmpty();
    }
}
