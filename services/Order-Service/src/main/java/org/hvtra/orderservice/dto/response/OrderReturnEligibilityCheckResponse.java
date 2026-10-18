package org.hvtra.orderservice.dto.response;

public record OrderReturnEligibilityCheckResponse(String code, boolean passed, String message) {
}
