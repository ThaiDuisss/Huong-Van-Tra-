import type Keycloak from "keycloak-js";
import type { TokenProvider } from "@hvt/api-client";

export interface KeycloakTokenProviderOptions {
    /**
     * Số giây tối thiểu token phải còn hiệu lực.
     *
     * Ví dụ 30 nghĩa là:
     * nếu token còn dưới 30 giây thì refresh trước khi gọi API.
     */
    minValiditySeconds?: number;

    /**
     * URL frontend nhận lại sau khi logout.
     */
    logoutRedirectUri?: string;

    /**
     * Callback khi refresh thất bại.
     */
    onRefreshError?: (
        error: unknown
    ) => void | Promise<void>;
}

export function createKeycloakTokenProvider(
    keycloak: Keycloak,
    options: KeycloakTokenProviderOptions = {}
): TokenProvider {
    const minValiditySeconds =
        options.minValiditySeconds ?? 30;

    return {
        async getAccessToken() {
            if (!keycloak.authenticated) {
                return null;
            }

            try {
                /*
                 * Nếu token còn dưới minValiditySeconds,
                 * Keycloak sẽ refresh token.
                 *
                 * Nếu token vẫn còn đủ hạn, không có request refresh.
                 */
                await keycloak.updateToken(minValiditySeconds);

                return keycloak.token ?? null;
            } catch (error) {
                await options.onRefreshError?.(error);

                keycloak.clearToken();

                return null;
            }
        },

        async refreshAccessToken() {
            if (!keycloak.authenticated) {
                return null;
            }

            try {
                /*
                 * -1 nghĩa là ép refresh ngay,
                 * kể cả token chưa gần hết hạn.
                 */
                await keycloak.updateToken(-1);

                return keycloak.token ?? null;
            } catch (error) {
                await options.onRefreshError?.(error);

                keycloak.clearToken();

                return null;
            }
        },

        clearTokens() {
            keycloak.clearToken();
        },

        login() {
            return keycloak.login({
                redirectUri: window.location.href
            });
        },

        logout() {
            return keycloak.logout({
                redirectUri:
                    options.logoutRedirectUri ??
                    window.location.origin
            });
        }
    };
}