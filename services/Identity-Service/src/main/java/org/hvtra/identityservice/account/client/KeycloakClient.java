package org.hvtra.identityservice.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** ClientRepresentation rút gọn: {@code id} là UUID nội bộ, {@code clientId} là tên client (vd. hvtra-pos). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakClient(String id, String clientId) {
}
