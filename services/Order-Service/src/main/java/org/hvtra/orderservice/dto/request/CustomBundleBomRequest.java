package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Một công thức (BOM) được chọn vào Bundle tổng hợp kèm ghi chú riêng của khách hàng. */
@Data
public class CustomBundleBomRequest {
    @NotNull
    private UUID variantId;

    @Size(max = 100)
    private String bomSku;

    @NotBlank
    @Size(max = 255)
    private String bomName;

    @Size(max = 1000)
    private String note;

    @NotEmpty
    private List<@Valid CustomBundleLineRequest> lines;
}
