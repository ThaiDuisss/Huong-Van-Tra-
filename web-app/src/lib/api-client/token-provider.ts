export interface TokenProvider {
    /**
     * Lấy access token hiện tại.
     *
     * Có thể chủ động refresh trước khi trả token.
     */
    getAccessToken(): string | null | Promise<string | null>;

    /**
     * Ép hệ thống xác thực làm mới access token.
     */
    refreshAccessToken?(): Promise<string | null>;

    /**
     * Xóa trạng thái xác thực cục bộ.
     */
    clearTokens?(): void | Promise<void>;

    /**
     * Chuyển người dùng sang trang đăng nhập.
     */
    login?(): void | Promise<void>;

    /**
     * Đăng xuất khỏi hệ thống xác thực.
     */
    logout?(): void | Promise<void>;
}