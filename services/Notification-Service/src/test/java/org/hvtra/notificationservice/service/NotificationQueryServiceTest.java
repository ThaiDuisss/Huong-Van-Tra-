package org.hvtra.notificationservice.service;

import org.hvtra.notificationservice.domain.entity.Notification;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(notificationRepository);
    }

    @Test
    @DisplayName("Danh sách thông báo của user được map sang response")
    void listsNotificationsOfUser() {
        Pageable pageable = PageRequest.of(0, 20);
        Notification notification = notification("UNREAD");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(notification), pageable, 1));

        Page<NotificationResponse> page = service.getNotifications("user-1", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(notification.getId());
        assertThat(page.getContent().get(0).orderCode()).isEqualTo("POS-00012");
    }

    @Test
    @DisplayName("Đếm số thông báo UNREAD")
    void countsUnread() {
        when(notificationRepository.countByUserIdAndStatus("user-1", "UNREAD")).thenReturn(3L);

        assertThat(service.unreadCount("user-1")).isEqualTo(3L);
    }

    @Test
    @DisplayName("Đánh dấu đã đọc ghi trạng thái READ và thời điểm đọc")
    void markReadSetsStatusAndReadAt() {
        Notification notification = notification("UNREAD");
        when(notificationRepository.findByIdAndUserId(notification.getId(), "user-1"))
                .thenReturn(Optional.of(notification));

        NotificationResponse response = service.markRead("user-1", notification.getId());

        assertThat(response.status()).isEqualTo("READ");
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    @DisplayName("Đánh dấu lại thông báo đã đọc không đổi thời điểm đọc cũ")
    void markReadKeepsOriginalReadAt() {
        Notification notification = notification("READ");
        Instant readAt = Instant.parse("2026-10-20T01:00:00Z");
        notification.setReadAt(readAt);
        when(notificationRepository.findByIdAndUserId(notification.getId(), "user-1"))
                .thenReturn(Optional.of(notification));

        assertThat(service.markRead("user-1", notification.getId()).readAt()).isEqualTo(readAt);
    }

    @Test
    @DisplayName("Thông báo của user khác hoặc không tồn tại trả 404")
    void markReadOfOtherUsersNotificationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(id, "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("user-2", id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("Đánh dấu tất cả đã đọc xử lý theo từng trang đến khi hết UNREAD")
    void markAllReadProcessesPagesUntilEmpty() {
        Notification first = notification("UNREAD");
        Notification second = notification("UNREAD");
        when(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(eq("user-1"), eq("UNREAD"), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)))
                .thenReturn(Page.empty());

        long updated = service.markAllRead("user-1");

        assertThat(updated).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo("READ");
        assertThat(second.getReadAt()).isNotNull();
        verify(notificationRepository, times(2)).flush();
    }

    @Test
    @DisplayName("Không có thông báo UNREAD thì trả 0")
    void markAllReadWithNothingUnread() {
        when(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(eq("user-1"), eq("UNREAD"), any()))
                .thenReturn(Page.empty());

        assertThat(service.markAllRead("user-1")).isZero();
    }

    private static Notification notification(String status) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId("user-1");
        notification.setType("ORDER_COMPLETED");
        notification.setTitle("Đơn hoàn tất");
        notification.setMessage("Đơn POS-00012 đã hoàn tất");
        notification.setOrderId("order-1");
        notification.setOrderCode("POS-00012");
        notification.setStatus(status);
        notification.setCreatedAt(Instant.parse("2026-10-26T02:00:00Z"));
        return notification;
    }
}
