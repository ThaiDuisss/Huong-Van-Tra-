package org.hvtra.identityservice.account.client;

import lombok.Getter;

/**
 * Lỗi khi gọi Keycloak Admin API. {@code status} là HTTP status Keycloak trả về,
 * hoặc 0 nếu không kết nối được, hoặc -1 nếu service chưa được cấu hình client admin.
 */
@Getter
public class KeycloakAdminException extends RuntimeException {

    public static final int STATUS_UNREACHABLE = 0;
    public static final int STATUS_NOT_CONFIGURED = -1;

    private final int status;

    public KeycloakAdminException(int status, String message) {
        super(message);
        this.status = status;
    }

    public KeycloakAdminException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
