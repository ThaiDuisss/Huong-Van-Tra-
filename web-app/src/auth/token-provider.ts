import type { TokenProvider } from "@/lib/api-client";

import { keycloak } from "./keycloak";

const DEFAULT_MIN_VALIDITY_SECONDS = 30;

let refreshPromise: Promise<string | null> | null = null;

function getUsableToken(minValiditySeconds = 0) {
    if (!keycloak.token) {
        return null;
    }

    try {
        return keycloak.isTokenExpired(minValiditySeconds)
            ? null
            : keycloak.token;
    } catch {
        return null;
    }
}

async function refreshAccessTokenQueued(
    minValiditySeconds: number,
    forceRefresh = false
) {
    if (!keycloak.authenticated) {
        return null;
    }

    if (!refreshPromise) {
        refreshPromise = keycloak
            .updateToken(forceRefresh ? -1 : minValiditySeconds)
            .then(() =>
                getUsableToken(forceRefresh ? 0 : minValiditySeconds)
            )
            .catch((error) => {
                console.error("Keycloak refresh token thất bại", error);
                return forceRefresh ? null : getUsableToken(minValiditySeconds);
            })
            .finally(() => {
                refreshPromise = null;
            });
    }

    return refreshPromise;
}

export function getQueuedAccessToken(
    minValiditySeconds = DEFAULT_MIN_VALIDITY_SECONDS
) {
    if (!keycloak.authenticated) {
        return Promise.resolve(null);
    }

    return refreshAccessTokenQueued(minValiditySeconds);
}

export const tokenProvider: TokenProvider = {
    getAccessToken() {
        return getQueuedAccessToken();
    },

    refreshAccessToken() {
        return refreshAccessTokenQueued(DEFAULT_MIN_VALIDITY_SECONDS, true);
    },

    clearTokens() {
        /*
         * Không clear Keycloak token tự động khi một API nền trả 401.
         * Nếu clear ở đây, AuthProvider sẽ chuyển authenticated=false và
         * khách đang thao tác POS bị đá về màn đăng nhập. Logout chủ động
         * vẫn đi qua keycloak.logout().
         */
    },

    login() {
        return keycloak.login({
            redirectUri: window.location.href
        });
    },

    logout() {
        return keycloak.logout({
            redirectUri: `${window.location.origin}/login`
        });
    }
};
