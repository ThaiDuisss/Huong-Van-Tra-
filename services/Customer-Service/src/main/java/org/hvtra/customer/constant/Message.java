package org.hvtra.customer.constant;

public final class Message {
    public static final String CUSTOMER_NOT_FOUND = "Không tìm thấy khách hàng";
    public static final String CUSTOMER_TIER_NOT_FOUND = "Không tìm thấy hạng khách hàng";
    public static final String CUSTOMER_ADDRESS_NOT_FOUND = "Không tìm thấy địa chỉ khách hàng";
    public static final String LOYALTY_ACCOUNT_NOT_FOUND = "Không tìm thấy tài khoản tích điểm";
    public static final String LOYALTY_TRANSACTION_NOT_FOUND = "Không tìm thấy giao dịch tích điểm";
    public static final String LOYALTY_POINT_RULE_NOT_FOUND = "Không tìm thấy quy tắc tích điểm";

    public static final String CUSTOMER_CODE_EXISTS = "Mã khách hàng đã tồn tại";
    public static final String CUSTOMER_TIER_CODE_EXISTS = "Mã hạng khách hàng đã tồn tại";
    public static final String CUSTOMER_PHONE_EXISTS = "Số điện thoại đã tồn tại";
    public static final String CUSTOMER_EMAIL_EXISTS = "Email đã tồn tại";
    public static final String LOYALTY_ACCOUNT_CUSTOMER_EXISTS = "Khách hàng đã có tài khoản tích điểm";
    public static final String LOYALTY_POINT_RULE_CODE_EXISTS = "Mã quy tắc tích điểm đã tồn tại";

    public static final String LOYALTY_TRANSACTION_POINTS_REQUIRED = "Số điểm giao dịch phải khác 0";

    private Message() {
    }
}
