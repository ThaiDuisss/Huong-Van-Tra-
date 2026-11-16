package org.hvtra.notificationservice.domain.entity;

import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    void prePersistDefaultsStatusAndTimestamps() {
        Notification notification = new Notification();
        Instant before = Instant.now();

        notification.prePersist();

        assertThat(notification.getStatus()).isEqualTo("UNREAD");
        assertThat(notification.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(notification.getUpdatedAt()).isEqualTo(notification.getCreatedAt());
    }

    @Test
    void prePersistKeepsExplicitStatusAndCreationTime() {
        Instant created = Instant.parse("2026-10-26T02:00:00Z");
        Notification notification = new Notification();
        notification.setStatus("READ");
        notification.setCreatedAt(created);

        notification.prePersist();

        assertThat(notification.getStatus()).isEqualTo("READ");
        assertThat(notification.getCreatedAt()).isEqualTo(created);
        assertThat(notification.getUpdatedAt()).isAfter(created);
    }

    @Test
    void preUpdateOnlyRefreshesUpdatedAt() {
        Instant created = Instant.parse("2026-10-26T02:00:00Z");
        Notification notification = new Notification();
        notification.setCreatedAt(created);
        notification.setUpdatedAt(created);

        notification.preUpdate();

        assertThat(notification.getCreatedAt()).isEqualTo(created);
        assertThat(notification.getUpdatedAt()).isAfter(created);
    }

    @Test
    void responseExposesOnlyUserFacingFields() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-26T02:00:00Z");
        Instant read = Instant.parse("2026-10-26T03:00:00Z");
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId("user-1");
        notification.setType("ORDER_CANCELLED");
        notification.setTitle("Đơn đã huỷ");
        notification.setMessage("Đơn POS-1 đã bị huỷ.");
        notification.setOrderId("order-1");
        notification.setOrderCode("POS-1");
        notification.setCreatedAt(created);
        notification.setReadAt(read);
        notification.setStatus("READ");

        assertThat(NotificationResponse.from(notification)).isEqualTo(new NotificationResponse(
                id, "ORDER_CANCELLED", "Đơn đã huỷ", "Đơn POS-1 đã bị huỷ.", "order-1", "POS-1", created, read, "READ"));
    }
}
