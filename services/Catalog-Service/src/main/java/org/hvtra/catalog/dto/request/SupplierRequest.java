package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hvtra.catalog.enums.Status;

import java.util.List;
import java.util.UUID;

public record SupplierRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 255, message = "Tên nhà cung cấp tối đa 255 ký tự")
        String name,

        @Size(max = 100, message = "Tên người liên hệ tối đa 100 ký tự")
        String contactName,

        @Size(max = 50, message = "Số điện thoại tối đa 50 ký tự")
        String phone,

        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @Size(max = 50, message = "Mã số thuế tối đa 50 ký tự")
        String taxCode,

        String notes,

        Status status,

        List<UUID> ingredientIds
) {
}
