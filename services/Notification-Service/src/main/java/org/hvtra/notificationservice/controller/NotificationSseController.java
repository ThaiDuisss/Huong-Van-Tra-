package org.hvtra.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.hvtra.notificationservice.service.NotificationSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationSseController {

    private final NotificationSseService notificationSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notificationSseService.connect(SecurityUtils.currentUserId());
    }
}
