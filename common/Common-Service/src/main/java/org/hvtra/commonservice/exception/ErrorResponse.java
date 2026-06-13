package org.hvtra.commonservice.exception;

public record ErrorResponse(String code,
                            String message,
                            int status
                            ) {
}
