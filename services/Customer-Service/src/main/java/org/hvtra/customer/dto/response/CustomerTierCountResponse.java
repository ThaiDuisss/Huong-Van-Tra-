package org.hvtra.customer.dto.response;

import java.util.UUID;

public record CustomerTierCountResponse(
        UUID id,
        String code,
        String name,
        String colorCode,
        long customerCount
) {
}
