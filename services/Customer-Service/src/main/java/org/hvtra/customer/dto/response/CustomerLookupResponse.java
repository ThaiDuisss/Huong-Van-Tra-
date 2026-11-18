package org.hvtra.customer.dto.response;

import java.util.UUID;

/** Dữ liệu tối thiểu cho dropdown / tìm khách: hiển thị name, code, phone; id là giá trị ẩn. */
public record CustomerLookupResponse(
        UUID id,
        String name,
        String code,
        String phone
) {
}
