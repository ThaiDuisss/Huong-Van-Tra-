package org.hvtra.commonservice.domain.entity;

import org.hvtra.commonservice.domain.constants.HeaderConstants;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UserHeaderInformation {
    private final String correlationId;
    private final String userId;
    private final String userEmail;
    private final List<String> userRoles;
    private final String authorization;

    private UserHeaderInformation(
            String correlationId,
            String userId,
            String userEmail,
            List<String> userRoles,
            String authorization
    ) {
        this.correlationId = correlationId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userRoles = userRoles == null ? Collections.emptyList() : Collections.unmodifiableList(userRoles);
        this.authorization = authorization;
    }

    public static UserHeaderInformation fromRequest(HttpServletRequest request) {
        if (request == null) {
            return new UserHeaderInformation(null, null, null, Collections.emptyList(), null);
        }

        String correlationId = request.getHeader(HeaderConstants.HEADER_CORRELATION_ID);
        String userId = request.getHeader(HeaderConstants.HEADER_USER_ID);
        String userEmail = request.getHeader(HeaderConstants.HEADER_USER_NAME);
        String rolesRaw = request.getHeader(HeaderConstants.HEADER_USER_ROLES);
        String authorization = request.getHeader(HeaderConstants.HEADER_AUTHOR);

        List<String> roles = parseRoles(rolesRaw);

        return new UserHeaderInformation(correlationId, userId, userEmail, roles, authorization);
    }

    private static List<String> parseRoles(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public List<String> getUserRoles() {
        return userRoles;
    }

    public String getAuthorization() {
        return authorization;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserHeaderInformation)) return false;
        UserHeaderInformation that = (UserHeaderInformation) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(userEmail, that.userEmail) &&
                Objects.equals(userRoles, that.userRoles) &&
                Objects.equals(authorization, that.authorization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, userId, userEmail, userRoles, authorization);
    }

    @Override
    public String toString() {
        return "UserHeaderInformation{" +
                "correlationId='" + correlationId + '\'' +
                ", userId='" + userId + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", userRoles=" + userRoles +
                ", authorization='" + (authorization != null ? "[REDACTED]" : null) + '\'' +
                '}';
    }
}