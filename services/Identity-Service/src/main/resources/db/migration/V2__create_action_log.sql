-- Bảng lưu hành động người dùng từ mọi service, nhận qua Kafka topic action.log
CREATE TABLE action_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id    VARCHAR(64)   NULL,
    user_id       VARCHAR(64)   NULL,
    username      VARCHAR(100)  NULL,
    service       VARCHAR(50)   NOT NULL,
    action        VARCHAR(100)  NOT NULL,
    entity_type   VARCHAR(100)  NULL,
    entity_id     VARCHAR(64)   NULL,
    http_method   VARCHAR(10)   NULL,
    path          VARCHAR(500)  NULL,
    ip            VARCHAR(45)   NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'SUCCESS',
    error_message VARCHAR(500)  NULL,
    duration_ms   BIGINT        NULL,
    detail        TEXT          NULL,
    occurred_at   DATETIME(3)   NOT NULL,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_action_log_user_time   (user_id, occurred_at),
    INDEX idx_action_log_request     (request_id),
    INDEX idx_action_log_entity      (entity_type, entity_id),
    INDEX idx_action_log_action_time (action, occurred_at),
    INDEX idx_action_log_occurred    (occurred_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
