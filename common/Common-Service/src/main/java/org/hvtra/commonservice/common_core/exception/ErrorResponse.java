package org.hvtra.commonservice.common_core.exception;

public record ErrorResponse(String code,
                            String message,
                            int status
                            ) {
}
