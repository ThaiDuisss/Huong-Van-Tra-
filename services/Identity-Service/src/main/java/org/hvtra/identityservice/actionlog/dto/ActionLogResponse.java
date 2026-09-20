package org.hvtra.identityservice.actionlog.dto;

import org.hvtra.identityservice.actionlog.entity.ActionLog;

import java.time.Instant;

public record ActionLogResponse(
        Long id,
        String requestId,
        String userId,
        String username,
        String service,
        String action,
        String entityType,
        String entityId,
        String httpMethod,
        String path,
        String ip,
        String status,
        String errorMessage,
        Long durationMs,
        String detail,
        Instant occurredAt
) {
    public static ActionLogResponse from(ActionLog a) {
        return new ActionLogResponse(a.getId(), a.getRequestId(), a.getUserId(), a.getUsername(), a.getService(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getHttpMethod(), a.getPath(), a.getIp(),
                a.getStatus(), a.getErrorMessage(), a.getDurationMs(), a.getDetail(), a.getOccurredAt());
    }
}
