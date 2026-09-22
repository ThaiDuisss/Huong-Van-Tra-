package org.hvtra.commonservice.importing.dto.response;

public record ValidationErrorResponse(
        String field,
        String message
) {
}
