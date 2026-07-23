package org.hvtra.apigateway.utils;

public class GatewayConstants {
    public  static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // Header
    public  static final String HEADER_CORRELATION_ID = "X-Request-ID";
    public  static final String HEADER_USER_ID = "X-User-ID";
    public  static final String HEADER_USER_ROLES = "X-User_Roles";
    public  static final String HEADER_AUTHOR = "Authorization";
    public static final String HEADER_SIGNATURE = "X-Signature";

    // Order
    public  static final int ORDER_CORRELATION_FILTER = -200;
    public  static final int ORDER_LOGGING_FILTER = -100;
    public  static final int ORDER_JWT_AUTH_FILTER = -90;


}
