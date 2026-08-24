package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String supplierCode,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        String taxCode,
        String notes,
        Status status,
        List<IngredientLookupResponse> ingredients,
        Instant createdAt,
        Instant updatedAt
) {
}
