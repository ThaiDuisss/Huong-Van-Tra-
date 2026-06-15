package org.hvtra.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.hvtra.catalog.enums.BarcodeType;

public record BarcodeUpdateRequest(
        @NotNull BarcodeType barcodeType,
        @JsonProperty("isPrimary") boolean primary
) {
}
