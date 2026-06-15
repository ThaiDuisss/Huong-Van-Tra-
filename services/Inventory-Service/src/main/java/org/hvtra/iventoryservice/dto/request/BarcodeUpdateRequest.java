package org.hvtra.iventoryservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.enums.BarcodeType;

public record BarcodeUpdateRequest(
        @NotNull BarcodeType barcodeType,
        @JsonProperty("isPrimary") boolean primary
) {
}
