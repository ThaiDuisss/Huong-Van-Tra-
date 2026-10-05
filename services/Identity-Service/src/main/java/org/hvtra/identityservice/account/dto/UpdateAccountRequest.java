package org.hvtra.identityservice.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateAccountRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotBlank(message = "Tên không được để trống")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        String firstName,

        @NotBlank(message = "Họ không được để trống")
        @Size(max = 100, message = "Họ tối đa 100 ký tự")
        String lastName,

        @NotEmpty(message = "Phải chọn ít nhất một vai trò")
        List<String> roles
) {
}
