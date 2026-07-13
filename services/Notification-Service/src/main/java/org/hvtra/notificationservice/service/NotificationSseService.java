package org.hvtra.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NotificationSseService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ex -> remove(userId, emitter));

        return emitter;
    }

    public void sendToUser(String userId, NotificationResponse notification) {
        if (userId == null || userId.isBlank() || notification == null) {
            return;
        }

        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .id(notification.id().toString())
                        .data(notification));
            } catch (IOException | RuntimeException ex) {
                if (isDisconnectedClient(ex)) {
                    log.debug("Remove disconnected notification SSE emitter. userId={}, notificationId={}",
                            userId, notification.id());
                } else {
                    log.warn("Remove failed notification SSE emitter. userId={}, notificationId={}",
                            userId, notification.id(), ex);
                }
                remove(userId, emitter);
            }
        }
    }

    private boolean isDisconnectedClient(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && (message.contains("Broken pipe")
                    || message.contains("disconnected client")
                    || message.contains("Connection reset"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }
}
