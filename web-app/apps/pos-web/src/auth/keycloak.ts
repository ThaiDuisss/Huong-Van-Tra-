import { createKeycloak } from "@hvt/auth";

export const keycloak = createKeycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM,
    clientId:
        import.meta.env.VITE_KEYCLOAK_CLIENT_ID
});

let initPromise: Promise<boolean> | null = null;

export function initKeycloak(): Promise<boolean> {
    if (!initPromise) {
        initPromise = keycloak.init({
            onLoad: "check-sso",
            pkceMethod: "S256",

            checkLoginIframe: false,

            silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        });
    }

    return initPromise;
}
