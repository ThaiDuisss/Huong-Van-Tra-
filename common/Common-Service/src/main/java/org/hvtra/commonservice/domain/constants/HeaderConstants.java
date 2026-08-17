package org.hvtra.commonservice.domain.constants;

import java.util.List;

public class HeaderConstants {
    private HeaderConstants() {}

    // Header
    public  static final String HEADER_CORRELATION_ID = "X-Request-ID";
    public  static final String HEADER_USER_ID = "X-User-ID";
    public  static final String HEADER_USER_ROLES = "X-User_Roles";
    public  static final String HEADER_AUTHOR = "Authorization";
    public static final String HEADER_SIGNATURE = "X-Signature";

    public static final List<String> FORWARD_HEADERS = List.of(
            HEADER_AUTHOR,
            HEADER_CORRELATION_ID,
            HEADER_USER_ID,
            HEADER_USER_ROLES,
            HEADER_SIGNATURE
    );
}
