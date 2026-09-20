package org.hvtra.identityservice.actionlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một hành động người dùng đã lưu. Insert bằng JdbcTemplate batch (xem ActionLogService),
 * entity này chủ yếu phục vụ truy vấn.
 */
@Entity
@Table(name = "action_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 50)
    private String service;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(length = 500)
    private String path;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
