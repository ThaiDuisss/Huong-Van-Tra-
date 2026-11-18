package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hvtra.customer.model.enums.CustomerStatus;

import java.time.LocalDate;

/**
 * Thông tin hồ sơ khách do người dùng nhập. Hạng, chi tiêu, số đơn và điểm do hệ thống tính,
 * không nhận từ request.
 */
public record CustomerRequest(
        @Size(max = 50) String customerCode,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        LocalDate dateOfBirth,
        CustomerStatus status
) {
}
