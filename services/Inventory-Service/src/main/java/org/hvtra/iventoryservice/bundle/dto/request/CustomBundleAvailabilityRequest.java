package org.hvtra.iventoryservice.bundle.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CustomBundleAvailabilityRequest(
        UUID branchId,
        @NotEmpty List<@Valid CustomBundleLineRequest> lines
) {
}
