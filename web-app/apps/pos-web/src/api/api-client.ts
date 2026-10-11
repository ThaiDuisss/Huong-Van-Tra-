import { createApiClient } from "@hvt/api-client";

import { networkMonitor } from "../app/network/network-status";
import { tokenProvider } from "../auth/token-provider";

export const apiClient = createApiClient({
    baseUrl:
        import.meta.env.VITE_API_BASE_URL ??
        "http://localhost:8080/api",

    /*
     * 30 giây cho request thường: đủ cho tìm sản phẩm, thêm giỏ, thanh toán.
     * Để lâu hơn thì khi mất mạng thu ngân phải đứng chờ rất lâu mới biết.
     * Upload/download (xuất Excel, nhập file) dùng longTimeoutMs riêng.
     */
    timeoutMs: 30_000,
    longTimeoutMs: 300_000,

    /*
     * Tự thử lại GET khi mất mạng (mặc định 2 lần, backoff 300ms → 600ms).
     * Request ghi dữ liệu không thử lại để tránh tạo đơn trùng.
     */
    retry: true,

    tokenProvider,

    defaultHeaders: {
        "X-Client-Type": "POS_WEB"
    },

    onRequestError(error) {
        /*
         * Chỉ lỗi không có phản hồi HTTP mới báo mất mạng. Timeout của một
         * request chậm không tính, tránh banner đỏ nháy khi mạng vẫn bình thường.
         */
        if (error.code === "NETWORK_ERROR") {
            networkMonitor.reportNetworkError();
        }
    },

    onRequestSuccess() {
        networkMonitor.reportSuccess();
    },

    onUnauthorized() {
        console.warn("Phiên đăng nhập đã hết hạn hoặc bị từ chối");

        /*
         * Đang mất mạng thì không chuyển hướng sang Keycloak: trang đăng nhập
         * cũng không tải được và thu ngân mất luôn màn POS. Giữ nguyên, khi
         * mạng về request kế tiếp sẽ refresh token hoặc đăng nhập lại.
         */
        if (!networkMonitor.getSnapshot().online) {
            return undefined;
        }

        return tokenProvider.login?.();
    }
});
