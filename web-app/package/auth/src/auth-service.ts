import type Keycloak from "keycloak-js";

export interface AuthUser {
    id: string;
    username?: string;
    email?: string;
    fullName?: string;

    realmRoles: string[];
    clientRoles: string[];
}

export interface AuthServiceOptions {
    clientId: string;
}

export function createAuthService(
    keycloak: Keycloak,
    options: AuthServiceOptions
) {
    return {
        isAuthenticated(): boolean {
            return keycloak.authenticated === true;
        },

        getAccessToken(): string | null {
            return keycloak.token ?? null;
        },

        getUser(): AuthUser | null {
            const token = keycloak.tokenParsed;

            if (!token || !keycloak.subject) {
                return null;
            }

            const realmRoles =
                keycloak.realmAccess?.roles ?? [];

            const clientRoles =
                keycloak.resourceAccess?.[
                    options.clientId
                ]?.roles ?? [];

            return {
                id: keycloak.subject,

                username:
                    typeof token.preferred_username ===
                        "string"
                        ? token.preferred_username
                        : undefined,

                email:
                    typeof token.email === "string"
                        ? token.email
                        : undefined,

                fullName:
                    typeof token.name === "string"
                        ? token.name
                        : undefined,

                realmRoles,
                clientRoles
            };
        },

        login(): Promise<void> {
            return keycloak.login({
                redirectUri: window.location.href
            });
        },

        logout(): Promise<void> {
            return keycloak.logout({
                redirectUri: window.location.origin
            });
        },

        async accountManagement(): Promise<void> {
            await keycloak.accountManagement();
        }
    };
}