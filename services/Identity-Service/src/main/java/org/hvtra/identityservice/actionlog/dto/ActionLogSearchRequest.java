package org.hvtra.identityservice.actionlog.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/** Tham số lọc của GET /api/identity/action-logs, bind từ query string. */
@Data
public class ActionLogSearchRequest {
    private String userId;
    private String username;
    private String service;
    private String action;
    private String entityType;
    private String entityId;
    private String requestId;
    private String status;
    /** ISO-8601, ví dụ 2026-09-01T00:00:00Z */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;
    private int page = 0;
    private int size = 20;
}
