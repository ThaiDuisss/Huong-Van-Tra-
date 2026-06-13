import Keycloak from "keycloak-js";

export interface KeycloakConfig {
    url: string;
    realm: string;
    clientId: string;
}

export function createKeycloak(config: KeycloakConfig): Keycloak {
    return new Keycloak({
        url: config.url,
        realm: config.realm,
        clientId: config.clientId
    });
}