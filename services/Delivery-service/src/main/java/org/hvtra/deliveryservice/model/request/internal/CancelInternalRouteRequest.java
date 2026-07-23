package org.hvtra.deliveryservice.model.request.internal;

public record CancelInternalRouteRequest(String reason) {
    public String resolvedReason() {
        return reason == null || reason.isBlank() ? "Route cancelled by dispatcher" : reason.trim();
    }
}
