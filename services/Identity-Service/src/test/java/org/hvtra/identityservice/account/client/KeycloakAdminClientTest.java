package org.hvtra.identityservice.account.client;

import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Kiểm tra giao tiếp HTTP với Keycloak bằng MockRestServiceServer (không gọi Keycloak thật). */
class KeycloakAdminClientTest {

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN_URL = BASE + "/realms/hvt/protocol/openid-connect/token";
    private static final String ADMIN = BASE + "/admin/realms/hvt";

    private MockRestServiceServer server;
    private MutableClock clock;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-10-02T00:00:00Z"));
        client = new KeycloakAdminClient(
                builder.build(),
                new KeycloakAdminProperties(BASE + "/", "hvt", "identity-admin", "test-secret"),
                clock);
    }

    @Test
    void cachesServiceAccountTokenUntilShortlyBeforeExpiry() {
        expectToken("token-1", 300);
        server.expect(ExpectedCount.twice(), requestTo(ADMIN + "/roles"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("[{\"id\":\"r1\",\"name\":\"ADMIN\"}]", MediaType.APPLICATION_JSON));
        expectToken("token-2", 300);
        server.expect(requestTo(ADMIN + "/roles"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listRealmRoles()).containsExactly(new KeycloakRole("r1", "ADMIN"));
        clock.advanceSeconds(200);
        client.listRealmRoles();
        // 300s - 30s dự phòng = 270s: tới giây 271 phải xin token mới
        clock.advanceSeconds(71);
        assertThat(client.listRealmRoles()).isEmpty();

        server.verify();
    }

    @Test
    void requestsTokenWithClientCredentials() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=identity-admin")))
                .andExpect(content().string(containsString("client_secret=test-secret")))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/u-1"))
                .andRespond(withSuccess(
                        "{\"id\":\"u-1\",\"username\":\"lan\",\"enabled\":true,\"attributes\":{\"phone\":[\"0901\"]}}",
                        MediaType.APPLICATION_JSON));

        KeycloakUser user = client.getUser("u-1");

        assertThat(user.getUsername()).isEqualTo("lan");
        assertThat(user.getOther()).containsKey("attributes");
        server.verify();
    }

    @Test
    void retriesOnceWithFreshToken_whenKeycloakReturns401() {
        expectToken("stale", 300);
        server.expect(requestTo(ADMIN + "/roles")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("fresh", 300);
        server.expect(requestTo(ADMIN + "/roles"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listRealmRoles()).isEmpty();
        server.verify();
    }

    @Test
    void resetPassword_putsTemporaryCredentialToResetPasswordEndpoint() {
        expectToken("token-1", 300);
        server.expect(requestTo(ADMIN + "/users/u-1/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.type").value("password"))
                .andExpect(jsonPath("$.value").value("Moi@12345"))
                .andExpect(jsonPath("$.temporary").value(true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.resetPassword("u-1", KeycloakUser.Credential.temporaryPassword("Moi@12345"));

        server.verify();
    }

    @Test
    void createUser_returnsIdFromLocationHeader_andSendsTemporaryCredential() {
        expectToken("token-1", 300);
        server.expect(requestTo(ADMIN + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("hoa.pham"))
                .andExpect(jsonPath("$.credentials[0].type").value("password"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).location(URI.create(ADMIN + "/users/new-id-123")));

        KeycloakUser user = new KeycloakUser();
        user.setUsername("hoa.pham");
        user.setCredentials(List.of(KeycloakUser.Credential.temporaryPassword("Init@12345")));

        assertThat(client.createUser(user)).isEqualTo("new-id-123");
        server.verify();
    }

    @Test
    void translatesKeycloakErrorBody() {
        expectToken("token-1", 300);
        server.expect(requestTo(ADMIN + "/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"User exists with same username\"}"));

        assertThatThrownBy(() -> client.createUser(new KeycloakUser()))
                .isInstanceOfSatisfying(KeycloakAdminException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo("User exists with same username");
                });
    }

    @Test
    void removeUserRealmRoles_sendsDeleteWithBody() {
        expectToken("token-1", 300);
        server.expect(requestTo(ADMIN + "/users/u-1/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$[0].name").value("SALE_STAFF"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.removeUserRealmRoles("u-1", List.of(new KeycloakRole("r-sale", "SALE_STAFF")));
        server.verify();
    }

    @Test
    void listUsersInRole_encodesRoleNameAndPages() {
        expectToken("token-1", 300);
        server.expect(requestTo(ADMIN + "/roles/SALE%20STAFF/users?first=0&max=200"))
                .andRespond(withSuccess("[{\"id\":\"u-1\",\"username\":\"lan\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.listUsersInRole("SALE STAFF")).extracting(KeycloakUser::getId).containsExactly("u-1");
        server.verify();
    }

    @Test
    void failsFastWithoutHttpCall_whenAdminClientNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer idleServer = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient unconfigured = new KeycloakAdminClient(
                builder.build(), new KeycloakAdminProperties(BASE, "hvt", "", ""), clock);

        assertThatThrownBy(unconfigured::listRealmRoles)
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(KeycloakAdminException.STATUS_NOT_CONFIGURED));
        idleServer.verify();
    }

    private void expectToken(String token, long expiresIn) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}",
                        MediaType.APPLICATION_JSON));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
