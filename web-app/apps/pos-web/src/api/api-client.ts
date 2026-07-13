import { createApiClient } from "@hvt/api-client";

import { tokenProvider } from "../auth/token-provider";

export const apiClient = createApiClient({
    baseUrl:
        import.meta.env.VITE_API_BASE_URL ??
        "http://localhost:8080/api",

    timeoutMs: 15_000,

    tokenProvider,

    defaultHeaders: {
        "X-Client-Type": "POS_WEB"
    },

    onUnauthorized() {
        /*
         * Không tự redirect sang Keycloak khi một request nền bị 401.
         * UI sẽ giữ nguyên màn hình hiện tại hoặc hiển thị nút đăng nhập
         * qua ProtectedRoute nếu phiên thật sự hết hạn.
         */
        console.warn("Phiên đăng nhập đã hết hạn hoặc bị từ chối");
    }
});
