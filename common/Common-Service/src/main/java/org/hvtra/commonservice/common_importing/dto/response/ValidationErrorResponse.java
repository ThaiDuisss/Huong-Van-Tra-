package org.hvtra.commonservice.common_importing.dto.response;

public record ValidationErrorResponse(
        String field,
        String message
) {
}
