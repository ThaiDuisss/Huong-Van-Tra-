package org.hvtra.identityservice.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAccountRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "Tên đăng nhập chỉ gồm chữ, số và các ký tự . _ - @")
        String username,

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

        /** Mật khẩu khởi tạo, Keycloak đánh dấu tạm thời: người dùng phải đổi ở lần đăng nhập đầu. */
        @NotBlank(message = "Mật khẩu khởi tạo không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu khởi tạo phải từ 8 đến 128 ký tự")
        String password,

        @NotEmpty(message = "Phải chọn ít nhất một vai trò")
        List<String> roles
) {
    /** Không in mật khẩu khi request bị log. */
    @Override
    public String toString() {
        return "CreateAccountRequest{username=" + username + ", email=" + email + ", roles=" + roles + "}";
    }
}
