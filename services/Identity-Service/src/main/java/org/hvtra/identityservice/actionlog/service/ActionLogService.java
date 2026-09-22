package org.hvtra.identityservice.actionlog.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.logging.action.ActionLogEvent;
import org.hvtra.identityservice.actionlog.config.ActionLogProperties;
import org.hvtra.identityservice.actionlog.dto.ActionLogResponse;
import org.hvtra.identityservice.actionlog.dto.ActionLogSearchRequest;
import org.hvtra.identityservice.actionlog.dto.PageResponse;
import org.hvtra.identityservice.actionlog.entity.ActionLog;
import org.hvtra.identityservice.actionlog.repository.ActionLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionLogService {
    private static final String INSERT_SQL = """
            INSERT INTO action_log
              (request_id, user_id, username, service, action, entity_type, entity_id,
               http_method, path, ip, status, error_message, duration_ms, detail, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final ActionLogRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ActionLogProperties properties;

    /** Chuyển event từ Kafka sang entity, cắt độ dài các cột để không lỗi insert. */
    public ActionLog toEntity(ActionLogEvent e) {
        return ActionLog.builder()
                .requestId(cut(e.getRequestId(), 64))
                .userId(cut(e.getUserId(), 64))
                .username(cut(e.getUsername(), 100))
                .service(cut(defaultIfBlank(e.getService(), "unknown"), 50))
                .action(cut(defaultIfBlank(e.getAction(), "UNKNOWN"), 100))
                .entityType(cut(e.getEntityType(), 100))
                .entityId(cut(e.getEntityId(), 64))
                .httpMethod(cut(e.getHttpMethod(), 10))
                .path(cut(e.getPath(), 500))
                .ip(cut(e.getIp(), 45))
                .status(cut(defaultIfBlank(e.getStatus(), "SUCCESS"), 10))
                .errorMessage(cut(e.getErrorMessage(), 500))
                .durationMs(e.getDurationMs())
                .detail(e.getDetail())
                .occurredAt(e.getOccurredAt() != null ? e.getOccurredAt() : Instant.now())
                .build();
    }

    /** Insert một lô bằng JDBC batch: một round-trip cho cả batch thay vì từng dòng. */
    @Transactional
    public int saveAll(List<ActionLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }
        List<Object[]> args = new ArrayList<>(logs.size());
        for (ActionLog a : logs) {
            args.add(new Object[]{
                    a.getRequestId(), a.getUserId(), a.getUsername(), a.getService(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getHttpMethod(), a.getPath(), a.getIp(),
                    a.getStatus(), a.getErrorMessage(), a.getDurationMs(), a.getDetail(),
                    Timestamp.from(a.getOccurredAt())
            });
        }
        int[] types = {
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.LONGVARCHAR, Types.TIMESTAMP
        };
        jdbcTemplate.batchUpdate(INSERT_SQL, args, types);
        return logs.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionLogResponse> search(ActionLogSearchRequest req) {
        int size = Math.min(Math.max(req.getSize(), 1), 200);
        var pageable = PageRequest.of(Math.max(req.getPage(), 0), size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        var page = repository.findAll(specification(req), pageable);
        return PageResponse.of(page, ActionLogResponse::from);
    }

    /** Xóa dữ liệu cũ hơn retentionDays theo từng lô. Trả về tổng số dòng đã xóa. */
    public long purgeExpired() {
        Instant before = Instant.now().minus(Duration.ofDays(properties.getRetentionDays())).truncatedTo(ChronoUnit.SECONDS);
        int batchSize = properties.getPurge().getBatchSize();
        long total = 0;
        int deleted;
        do {
            deleted = deleteBatch(before, batchSize);
            total += deleted;
        } while (deleted >= batchSize);
        if (total > 0) {
            log.info("Purged {} action_log rows older than {}", total, before);
        }
        return total;
    }
    @Transactional
    protected int deleteBatch(Instant before, int batchSize) {
        return repository.deleteOlderThan(before, batchSize);
    }

    private static Specification<ActionLog> specification(ActionLogSearchRequest r) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(r.getUserId()))
                p.add(cb.equal(root.get("userId"), r.getUserId()));

            if (StringUtils.hasText(r.getUsername()))
                p.add(cb.like(
                        cb.lower(root.get("username")),
                        "%" + r.getUsername().trim().toLowerCase() + "%"
                ));

            if (StringUtils.hasText(r.getService()))
                p.add(cb.equal(root.get("service"), r.getService()));

            if (StringUtils.hasText(r.getAction()))
                p.add(cb.like(
                        cb.lower(root.get("action")),
                        "%" + r.getAction().trim().toLowerCase() + "%"
                ));

            if (StringUtils.hasText(r.getEntityType()))
                p.add(cb.like(
                        cb.lower(root.get("entityType")),
                        "%" + r.getEntityType().trim().toLowerCase() + "%"
                ));

            if (StringUtils.hasText(r.getEntityId()))
                p.add(cb.like(
                        cb.lower(root.get("entityId")),
                        "%" + r.getEntityId().trim().toLowerCase() + "%"
                ));

            if (StringUtils.hasText(r.getRequestId()))
                p.add(cb.like(
                        cb.lower(root.get("requestId")),
                        "%" + r.getRequestId().trim().toLowerCase() + "%"
                ));

            if (StringUtils.hasText(r.getStatus()))
                p.add(cb.equal(root.get("status"), r.getStatus()));
            if (r.getFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), r.getFrom()));
            if (r.getTo() != null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"), r.getTo()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }


    private static String cut(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String defaultIfBlank(String v, String def) {
        return StringUtils.hasText(v) ? v : def;
    }

}
