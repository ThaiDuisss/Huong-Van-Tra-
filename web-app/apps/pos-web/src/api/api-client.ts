import { createApiClient } from "@hvt/api-client";

import { tokenProvider } from "../auth/token-provider";

export const apiClient = createApiClient({
    baseUrl:
        import.meta.env.VITE_API_BASE_URL ??
        "http://localhost:8080/api",

    timeoutMs: 300_000,

    tokenProvider,

    defaultHeaders: {
        "X-Client-Type": "POS_WEB"
    },

    onUnauthorized() {
        console.warn("Phiên đăng nhập đã hết hạn hoặc bị từ chối");
        return tokenProvider.login?.();
    }
});
