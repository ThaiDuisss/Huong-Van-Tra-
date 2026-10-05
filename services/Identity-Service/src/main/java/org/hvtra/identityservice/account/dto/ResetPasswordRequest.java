package org.hvtra.identityservice.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        /** Mật khẩu mới, Keycloak đánh dấu tạm thời: người dùng phải đổi ở lần đăng nhập kế tiếp. */
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu mới phải từ 8 đến 128 ký tự")
        String password
) {
    /** Không in mật khẩu khi request bị log. */
    @Override
    public String toString() {
        return "ResetPasswordRequest{}";
    }
}
