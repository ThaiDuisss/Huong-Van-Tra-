package org.hvtra.deliveryservice.carrier.model;

public record CarrierWebhookAck(boolean success, String message) {
    public static CarrierWebhookAck accepted() {
        return new CarrierWebhookAck(true, "accepted");
    }

    public static CarrierWebhookAck duplicate() {
        return new CarrierWebhookAck(true, "duplicate");
    }
}
