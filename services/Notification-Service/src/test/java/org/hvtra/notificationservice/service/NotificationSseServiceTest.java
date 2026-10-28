package org.hvtra.notificationservice.service;

import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationSseServiceTest {

    private NotificationSseService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSseService();
    }

    @Test
    @DisplayName("Kết nối SSE có timeout 30 phút và được ghi theo user")
    void connectRegistersEmitterWithThirtyMinuteTimeout() {
        SseEmitter first = service.connect("user-1");
        SseEmitter second = service.connect("user-1");

        assertThat(first.getTimeout()).isEqualTo(30 * 60 * 1000L);
        assertThat(emitters()).containsOnlyKeys("user-1");
        assertThat(emitters().get("user-1")).containsExactly(first, second);
    }

    @Test
    @DisplayName("Gửi cho user không có kết nối, userId rỗng hoặc notification null thì bỏ qua")
    void sendIgnoresMissingTargets() {
        service.connect("user-1");

        assertThatCode(() -> {
            service.sendToUser("user-2", notification());
            service.sendToUser(null, notification());
            service.sendToUser("  ", notification());
            service.sendToUser("user-1", null);
        }).doesNotThrowAnyException();
        assertThat(emitters().get("user-1")).hasSize(1);
    }

    @Test
    @DisplayName("Gửi tới mọi kết nối đang mở của user mà không ném lỗi")
    void sendDeliversToOpenEmitters() {
        service.connect("user-1");
        service.connect("user-1");

        assertThatCode(() -> service.sendToUser("user-1", notification())).doesNotThrowAnyException();
        assertThat(emitters().get("user-1")).hasSize(2);
    }

    @Test
    @DisplayName("Kết nối đã đóng bị gỡ khi gửi lỗi, kết nối còn lại vẫn giữ")
    void failedEmitterIsRemovedAndOthersKept() {
        SseEmitter closed = service.connect("user-1");
        SseEmitter open = service.connect("user-1");
        closed.complete();

        service.sendToUser("user-1", notification());

        assertThat(emitters().get("user-1")).containsExactly(open);
    }

    @Test
    @DisplayName("Gỡ kết nối cuối cùng thì xoá user khỏi danh sách")
    void lastEmitterRemovalDropsUser() {
        SseEmitter only = service.connect("user-1");
        only.complete();

        service.sendToUser("user-1", notification());

        assertThat(emitters()).doesNotContainKey("user-1");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<SseEmitter>> emitters() {
        return (Map<String, List<SseEmitter>>) ReflectionTestUtils.getField(service, "emittersByUser");
    }

    private static NotificationResponse notification() {
        return new NotificationResponse(UUID.randomUUID(), "ORDER_COMPLETED", "Đơn hoàn tất",
                "Đơn POS-00012 đã hoàn tất", "order-1", "POS-00012", Instant.now(), null, "UNREAD");
    }
}
