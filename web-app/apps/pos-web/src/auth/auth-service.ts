import { createAuthService } from "@hvt/auth";

import { keycloak } from "./keycloak";

export const authService = createAuthService(
    keycloak,
    {
        clientId:
            import.meta.env.VITE_KEYCLOAK_CLIENT_ID
    }
);