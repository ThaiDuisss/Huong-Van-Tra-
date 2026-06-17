package org.hvtra.catalog.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.catalog.enums.BarcodeType;

public record BarcodeCreateRequest(
        @NotBlank @Size(max = 255) String barcodeValue,
        @NotNull BarcodeType barcodeType,
        @JsonProperty("isPrimary") boolean primary
) {
}
