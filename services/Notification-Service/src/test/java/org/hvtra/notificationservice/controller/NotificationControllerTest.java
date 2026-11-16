package org.hvtra.notificationservice.controller;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.dto.response.UnreadCountResponse;
import org.hvtra.notificationservice.service.NotificationQueryService;
import org.hvtra.notificationservice.service.NotificationSseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The current user always comes from the security context, never from request data. */
class NotificationControllerTest {

    private final NotificationQueryService queryService = mock(NotificationQueryService.class);
    private final NotificationSseService sseService = mock(NotificationSseService.class);
    private final NotificationController controller = new NotificationController(queryService);
    private final NotificationSseController sseController = new NotificationSseController(sseService);

    @BeforeEach
    void authenticate() {
        InternalUserPrincipal principal =
                new InternalUserPrincipal("user-1", "cashier", "fixture-issuer", Set.of("SALE_STAFF"), "corr-1");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private NotificationResponse response(UUID id) {
        return new NotificationResponse(id, "ORDER_CANCELLED", "Đơn đã huỷ", "Đơn POS-1 đã bị huỷ.", "order-1",
                "POS-1", Instant.parse("2026-10-26T02:00:00Z"), null, "UNREAD");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 20, 0, 20",
            "3, 50, 3, 50",
            "-1, 20, 0, 20",
            "0, 0, 0, 1",
            "0, -7, 0, 1",
            "0, 100, 0, 100",
            "0, 101, 0, 100",
            "2, 2147483647, 2, 100"
    })
    void pageAndSizeAreClampedAndSortedNewestFirst(int page, int size, int expectedPage, int expectedSize) {
        NotificationResponse item = response(UUID.randomUUID());
        when(queryService.getNotifications(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        Page<NotificationResponse> result = controller.getNotifications(page, size);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).getNotifications(eq("user-1"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(captor.getValue().getPageSize()).isEqualTo(expectedSize);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(result.getContent()).containsExactly(item);
    }

    @Test
    void unreadCountIsScopedToCurrentUser() {
        when(queryService.unreadCount("user-1")).thenReturn(4L);

        assertThat(controller.unreadCount()).isEqualTo(new UnreadCountResponse(4L));
    }

    @Test
    void markReadIsScopedToCurrentUser() {
        UUID id = UUID.randomUUID();
        NotificationResponse read = response(id);
        when(queryService.markRead("user-1", id)).thenReturn(read);

        assertThat(controller.markRead(id)).isSameAs(read);
    }

    @Test
    void markAllReadReportsNumberOfUpdatedNotifications() {
        when(queryService.markAllRead("user-1")).thenReturn(7L);

        assertThat(controller.markAllRead()).isEqualTo(Map.of("updated", 7L));
    }

    @Test
    void streamConnectsEmitterForCurrentUser() {
        SseEmitter emitter = new SseEmitter();
        when(sseService.connect("user-1")).thenReturn(emitter);

        assertThat(sseController.stream()).isSameAs(emitter);
    }

    @Test
    void unauthenticatedCallerIsDeniedBeforeAnyServiceCall() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.getNotifications(0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(controller::unreadCount).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.markRead(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(controller::markAllRead).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(sseController::stream).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(queryService, sseService);
    }

    @Test
    void principalThatIsNotAnInternalUserIsDenied() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null, List.of()));

        assertThatThrownBy(controller::markAllRead).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(sseController::stream).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(queryService, sseService);
    }
}
