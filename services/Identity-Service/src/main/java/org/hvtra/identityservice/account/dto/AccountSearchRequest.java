package org.hvtra.identityservice.account.dto;

import lombok.Getter;
import lombok.Setter;

/** Query string của GET /api/identity/accounts. */
@Getter
@Setter
public class AccountSearchRequest {
    /** Tìm theo tên đăng nhập, email hoặc họ tên (chứa, không phân biệt hoa thường). */
    private String keyword;
    /** Một trong các StaffRole, vd. SALE_STAFF. */
    private String role;
    /** true = đang hoạt động, false = đã khóa, null = tất cả. */
    private Boolean enabled;
    private int page = 0;
    private int size = 20;
}
