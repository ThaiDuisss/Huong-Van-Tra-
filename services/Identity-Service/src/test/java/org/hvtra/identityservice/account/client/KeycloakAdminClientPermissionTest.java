package org.hvtra.identityservice.account.client;

import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Các endpoint Keycloak Admin dùng cho permission: client, client role, composite, effective role. */
class KeycloakAdminClientPermissionTest {

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN_URL = BASE + "/realms/hvt/protocol/openid-connect/token";
    private static final String ADMIN = BASE + "/admin/realms/hvt";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder.build(),
                new KeycloakAdminProperties(BASE, "hvt", "identity-admin", "test-secret", "hvtra-pos"),
                Clock.systemUTC());
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    @Test
    void resolvesClientUuidByClientIdExactly() {
        server.expect(requestTo(ADMIN + "/clients?clientId=hvtra-pos"))
                .andRespond(withSuccess("[{\"id\":\"c-uuid\",\"clientId\":\"hvtra-pos\"},{\"id\":\"x\",\"clientId\":\"hvtra-pos-web\"}]",
                        MediaType.APPLICATION_JSON));

        KeycloakClient found = client.findClientByClientId("hvtra-pos");

        assertThat(found.id()).isEqualTo("c-uuid");
        server.verify();
    }

    @Test
    void createsClientRoleWithDescriptionAndDisplayNameAttribute() {
        server.expect(requestTo(ADMIN + "/clients/c-uuid/roles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"order:create\"")))
                .andExpect(content().string(containsString("\"description\":\"Bán hàng\"")))
                .andExpect(content().string(containsString("\"displayName\":[\"Tạo đơn\"]")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));

        client.createClientRole("c-uuid", new KeycloakRole(null, "order:create", "Bán hàng", null, true,
                java.util.Map.of("displayName", List.of("Tạo đơn"))));

        server.verify();
    }

    @Test
    void duplicateClientRoleSurfacesAs409() {
        server.expect(requestTo(ADMIN + "/clients/c-uuid/roles"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"Role with name order:create already exists\"}"));

        assertThatThrownBy(() -> client.createClientRole("c-uuid", new KeycloakRole(null, "order:create")))
                .isInstanceOf(KeycloakAdminException.class)
                .satisfies(ex -> assertThat(((KeycloakAdminException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void readsCompositeClientRolesAndEffectiveUserRoles() {
        server.expect(requestTo(ADMIN + "/roles/SALE_STAFF/composites/clients/c-uuid"))
                .andRespond(withSuccess("[{\"id\":\"p1\",\"name\":\"order:view\",\"clientRole\":true}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/u-1/role-mappings/clients/c-uuid/composite"))
                .andRespond(withSuccess("[{\"id\":\"p1\",\"name\":\"order:view\"},{\"id\":\"p2\",\"name\":\"order:create\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getRealmRoleClientComposites("SALE_STAFF", "c-uuid"))
                .extracting(KeycloakRole::name).containsExactly("order:view");
        assertThat(client.getUserEffectiveClientRoles("u-1", "c-uuid"))
                .extracting(KeycloakRole::name).containsExactly("order:view", "order:create");
        server.verify();
    }

    @Test
    void addsAndRemovesCompositesWithIdAndNameOnly() {
        server.expect(requestTo(ADMIN + "/roles/SALE_STAFF/composites"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"p2\",\"name\":\"order:create\"}]"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        server.expect(requestTo(ADMIN + "/roles/SALE_STAFF/composites"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("[{\"id\":\"p1\",\"name\":\"order:view\"}]"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));

        client.addRealmRoleComposites("SALE_STAFF", List.of(new KeycloakRole("p2", "order:create")));
        client.removeRealmRoleComposites("SALE_STAFF", List.of(new KeycloakRole("p1", "order:view")));

        server.verify();
    }

    @Test
    void paginatesClientRoles() {
        StringBuilder page = new StringBuilder("[");
        for (int i = 0; i < 200; i++) {
            page.append(i > 0 ? "," : "").append("{\"id\":\"p").append(i).append("\",\"name\":\"m:a").append(i).append("\"}");
        }
        page.append("]");
        server.expect(requestTo(ADMIN + "/clients/c-uuid/roles?first=0&max=200"))
                .andRespond(withSuccess(page.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/clients/c-uuid/roles?first=200&max=200"))
                .andRespond(withSuccess("[{\"id\":\"p200\",\"name\":\"m:last\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.listClientRoles("c-uuid")).hasSize(201);
        server.verify();
    }
}
