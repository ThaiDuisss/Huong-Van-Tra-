package org.hvtra.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantCostResponse(
        UUID id,
        BigDecimal costPrice
) {}
