package org.hvtra.customer.dto.response;

import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        UUID customerId,
        String recipientName,
        String recipientPhone,
        String addressLine,
        String province,
        String district,
        String ward,
        Boolean isDefault
) {
}
