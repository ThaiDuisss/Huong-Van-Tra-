package org.hvtra.commonservice.common_core.domain.constants;

import java.util.List;

public class HeaderConstants {
    private HeaderConstants() {}

    // Header
    public  static final String HEADER_CORRELATION_ID = "X-Request-ID";
    public  static final String HEADER_USER_ID = "X-User-ID";
    public  static final String HEADER_USER_ROLES = "X-User_Roles";
    public  static final String HEADER_AUTHOR = "Authorization";
    public static final String HEADER_SIGNATURE = "X-Signature";
    /**
     * Permission hiệu lực của user (client role của client API trên Keycloak), dạng CSV, do gateway đặt.
     * Có chữ ký riêng ({@link #HEADER_PERMISSIONS_SIGNATURE}) để service cũ chưa biết header này vẫn xác thực được.
     */
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";
    public static final String HEADER_PERMISSIONS_SIGNATURE = "X-Permissions-Signature";

    public static final List<String> FORWARD_HEADERS = List.of(
            HEADER_AUTHOR,
            HEADER_CORRELATION_ID,
            HEADER_USER_ID,
            HEADER_USER_ROLES,
            HEADER_SIGNATURE,
            HEADER_USER_PERMISSIONS,
            HEADER_PERMISSIONS_SIGNATURE
    );
}
