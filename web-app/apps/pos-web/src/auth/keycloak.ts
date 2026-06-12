import { createKeycloak } from "@hvt/auth";

export const keycloak = createKeycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM,
    clientId:
        import.meta.env.VITE_KEYCLOAK_CLIENT_ID
});