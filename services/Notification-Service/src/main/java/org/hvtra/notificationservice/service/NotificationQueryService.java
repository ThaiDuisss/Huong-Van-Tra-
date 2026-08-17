package org.hvtra.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.notificationservice.domain.entity.Notification;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final String UNREAD = "UNREAD";
    private static final String READ = "READ";

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndStatus(userId, UNREAD);
    }

    @Transactional
    public NotificationResponse markRead(String userId, UUID id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy notification"));

        markAsRead(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public long markAllRead(String userId) {
        Page<Notification> page;
        long updated = 0;

        do {
            page = notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    UNREAD,
                    org.springframework.data.domain.PageRequest.of(0, 200)
            );

            for (Notification notification : page.getContent()) {
                markAsRead(notification);
                updated++;
            }

            notificationRepository.flush();
        } while (!page.isEmpty());

        return updated;
    }

    private void markAsRead(Notification notification) {
        if (READ.equals(notification.getStatus())) {
            return;
        }
        notification.setStatus(READ);
        notification.setReadAt(Instant.now());
    }
}
