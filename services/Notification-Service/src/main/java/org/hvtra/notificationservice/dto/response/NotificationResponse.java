package org.hvtra.notificationservice.dto.response;

import org.hvtra.notificationservice.domain.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String message,
        String orderId,
        String orderCode,
        Instant createdAt,
        Instant readAt,
        String status
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getOrderId(),
                notification.getOrderCode(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getStatus()
        );
    }
}
