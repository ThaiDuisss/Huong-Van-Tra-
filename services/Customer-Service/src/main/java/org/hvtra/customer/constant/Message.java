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

    public static final String CUSTOMER_IMPORT_EMPTY = "Danh sách khách hàng cần nhập đang trống";
    public static final String CUSTOMER_IMPORT_TOO_MANY_ROWS = "Mỗi lần chỉ được nhập tối đa %d khách hàng";
    public static final String CUSTOMER_IMPORT_EMPTY_ROW = "Dòng không có dữ liệu";
    public static final String CUSTOMER_IMPORT_PHONE_INVALID = "Số điện thoại không đúng định dạng (8-15 chữ số, có thể bắt đầu bằng +)";
    public static final String CUSTOMER_IMPORT_EMAIL_INVALID = "Email không đúng định dạng";
    public static final String CUSTOMER_IMPORT_DOB_INVALID = "Ngày sinh không đúng định dạng (yyyy-MM-dd hoặc dd/MM/yyyy)";
    public static final String CUSTOMER_IMPORT_DOB_FUTURE = "Ngày sinh không được lớn hơn ngày hiện tại";
    public static final String CUSTOMER_IMPORT_TYPE_INVALID = "Loại khách hàng không hợp lệ (Cá nhân hoặc Doanh nghiệp)";
    public static final String CUSTOMER_IMPORT_DUPLICATE_IN_FILE = "%s trùng với dòng %d trong file";
    public static final String CUSTOMER_IMPORT_ROW_VALID = "Hợp lệ";
    public static final String CUSTOMER_IMPORT_ROW_IMPORTED = "Đã nhập";
    public static final String CUSTOMER_IMPORT_ROW_FAILED = "Không thể tạo khách hàng, vui lòng thử lại";

    private Message() {
    }
}
