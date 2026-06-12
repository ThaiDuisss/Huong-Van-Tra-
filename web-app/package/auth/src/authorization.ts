import type Keycloak from "keycloak-js";

export function hasRealmRole(
    keycloak: Keycloak,
    role: string
): boolean {
    return keycloak.hasRealmRole(role);
}

export function hasClientRole(
    keycloak: Keycloak,
    role: string,
    clientId?: string
): boolean {
    return keycloak.hasResourceRole(
        role,
        clientId
    );
}

export function hasAnyClientRole(
    keycloak: Keycloak,
    roles: string[],
    clientId?: string
): boolean {
    return roles.some((role) =>
        keycloak.hasResourceRole(
            role,
            clientId
        )
    );
}

export function hasAllClientRoles(
    keycloak: Keycloak,
    roles: string[],
    clientId?: string
): boolean {
    return roles.every((role) =>
        keycloak.hasResourceRole(
            role,
            clientId
        )
    );
}