import { createApiClient } from "@hvt/api-client";
import {
    createKeycloakTokenProvider
} from "@hvt/auth";

import { keycloak } from "../auth/keycloak";

const tokenProvider =
    createKeycloakTokenProvider(keycloak, {
        minValiditySeconds: 30,

        logoutRedirectUri:
            window.location.origin,

        onRefreshError(error) {
            console.error(
                "Keycloak refresh thất bại",
                error
            );
        }
    });

export const apiClient = createApiClient({
    baseUrl:
        import.meta.env.VITE_API_BASE_URL ??
        "http://localhost:8080/api",

    timeoutMs: 15_000,

    // tokenProvider,

    defaultHeaders: {
        "X-Client-Type": "POS_WEB"
    },

    onUnauthorized() {
        /*
         * Không tự gọi API login backend.
         * Chuyển người dùng sang Keycloak.
         */
        return tokenProvider.login?.();
    }
});
