package org.hvtra.identityservice.account.client;

/**
 * Lời gọi Keycloak bị từ chối ngay tại Identity-Service vì circuit breaker {@code keycloak} đang OPEN
 * hoặc bulkhead đã đầy: KHÔNG có request nào được gửi tới Keycloak. Mang status
 * {@link #STATUS_UNREACHABLE} để AccountService/PermissionService dịch thành HTTP 503
 * {@code KEYCLOAK_UNAVAILABLE} giống trường hợp không kết nối được.
 */
public class KeycloakUnavailableException extends KeycloakAdminException {

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(STATUS_UNREACHABLE, message, cause);
    }
}
