package org.hvtra.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.dto.response.UnreadCountResponse;
import org.hvtra.notificationservice.service.NotificationQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String currentUserId = SecurityUtils.currentUserId();
        return notificationQueryService.getNotifications(
                currentUserId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        String currentUserId = SecurityUtils.currentUserId();
        return new UnreadCountResponse(notificationQueryService.unreadCount(currentUserId));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        String currentUserId = SecurityUtils.currentUserId();
        return notificationQueryService.markRead(currentUserId, id);
    }

    @PatchMapping("/read-all")
    public Map<String, Long> markAllRead() {
        String currentUserId = SecurityUtils.currentUserId();
        return Map.of("updated", notificationQueryService.markAllRead(currentUserId));
    }
}
