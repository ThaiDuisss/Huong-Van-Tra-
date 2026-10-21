package org.hvtra.identityservice.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** RoleRepresentation rút gọn: Keycloak chỉ cần id + name khi gán/gỡ role. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {
}
