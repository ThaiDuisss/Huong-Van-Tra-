package org.hvtra.orderservice.dto.response;

import java.util.UUID;

/** Kết quả GET /api/customer/lookup của Customer-Service. */
public record CustomerLookupResponse(
        UUID id,
        String name,
        String code,
        String phone
) {
}
