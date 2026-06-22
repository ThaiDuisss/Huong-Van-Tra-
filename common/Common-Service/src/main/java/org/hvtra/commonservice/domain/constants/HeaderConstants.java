package org.hvtra.commonservice.domain.constants;

import java.util.List;

public class HeaderConstants {
    private HeaderConstants() {}

    // Header
    public  static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public  static final String HEADER_USER_ID = "X-User-ID";
    public  static final String HEADER_USER_NAME = "X-User_Email";
    public  static final String HEADER_USER_ROLES = "X-User_Roles";
    public  static final String HEADER_AUTHOR = "Authorization";

    public static final List<String> FORWARD_HEADERS = List.of(
            HEADER_AUTHOR,
            HEADER_CORRELATION_ID,
            HEADER_USER_ID,
            HEADER_USER_NAME,
            HEADER_USER_ROLES
    );
}
