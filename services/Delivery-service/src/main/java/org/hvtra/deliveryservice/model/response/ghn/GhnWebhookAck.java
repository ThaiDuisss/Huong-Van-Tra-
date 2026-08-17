package org.hvtra.deliveryservice.model.response.ghn;

public record GhnWebhookAck(int code, String message) {
    public static GhnWebhookAck success() {
        return new GhnWebhookAck(200, "Success");
    }
}
