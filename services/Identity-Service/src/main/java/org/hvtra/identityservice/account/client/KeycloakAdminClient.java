package org.hvtra.identityservice.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Gọi Keycloak Admin REST API bằng token service-account (client_credentials).
 * Token được cache tới gần lúc hết hạn; nếu Keycloak trả 401 thì xin token mới và thử lại một lần.
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    /** Xin token mới sớm hơn thời điểm hết hạn một khoảng để tránh dùng token sắp hết hạn. */
    static final long EXPIRY_SKEW_SECONDS = 30;
    private static final int ROLE_MEMBER_PAGE_SIZE = 200;

    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLE_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<KeycloakClient>> CLIENT_LIST = new ParameterizedTypeReference<>() {
    };
    private static final int ROLE_PAGE_SIZE = 200;
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public KeycloakAdminClient(@Qualifier("keycloakAdminRestClient") RestClient restClient,
                               KeycloakAdminProperties properties) {
        this(restClient, properties, Clock.systemUTC());
    }

    KeycloakAdminClient(RestClient restClient, KeycloakAdminProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    public List<KeycloakRole> listRealmRoles() {
        List<KeycloakRole> roles = execute(token -> restClient.get()
                .uri(properties.adminUrl("/roles"), properties.getRealm())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(ROLE_LIST));
        return roles == null ? List.of() : roles;
    }

    /** Toàn bộ user được gán trực tiếp realm role này (tự phân trang). */
    public List<KeycloakUser> listUsersInRole(String roleName) {
        List<KeycloakUser> result = new ArrayList<>();
        int first = 0;
        while (true) {
            int offset = first;
            List<KeycloakUser> page = execute(token -> restClient.get()
                    .uri(properties.adminUrl("/roles/{role}/users?first={first}&max={max}"),
                            properties.getRealm(), roleName, offset, ROLE_MEMBER_PAGE_SIZE)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(USER_LIST));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < ROLE_MEMBER_PAGE_SIZE) {
                break;
            }
            first += ROLE_MEMBER_PAGE_SIZE;
        }
        return result;
    }

    public KeycloakUser getUser(String userId) {
        return execute(token -> restClient.get()
                .uri(properties.adminUrl("/users/{id}"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(KeycloakUser.class));
    }

    /** Tạo user và trả về id (lấy từ header Location của Keycloak). */
    public String createUser(KeycloakUser user) {
        ResponseEntity<Void> response = execute(token -> restClient.post()
                .uri(properties.adminUrl("/users"), properties.getRealm())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
        URI location = response.getHeaders().getLocation();
        if (location == null || !StringUtils.hasText(location.getPath())) {
            throw new KeycloakAdminException(502, "Keycloak không trả về id của user vừa tạo");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public void updateUser(String userId, KeycloakUser user) {
        execute(token -> restClient.put()
                .uri(properties.adminUrl("/users/{id}"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteUser(String userId) {
        execute(token -> restClient.delete()
                .uri(properties.adminUrl("/users/{id}"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
    }

    /** Đặt lại mật khẩu của user. Credential tạm thời buộc user đổi mật khẩu ở lần đăng nhập kế tiếp. */
    public void resetPassword(String userId, KeycloakUser.Credential credential) {
        execute(token -> restClient.put()
                .uri(properties.adminUrl("/users/{id}/reset-password"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(credential)
                .retrieve()
                .toBodilessEntity());
    }

    /** Đăng xuất mọi phiên của user (dùng khi khóa tài khoản hoặc đặt lại mật khẩu). */
    public void logoutUser(String userId) {
        execute(token -> restClient.post()
                .uri(properties.adminUrl("/users/{id}/logout"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
    }

    /** Realm role được gán trực tiếp cho user. */
    public List<KeycloakRole> getUserRealmRoles(String userId) {
        List<KeycloakRole> roles = execute(token -> restClient.get()
                .uri(properties.adminUrl("/users/{id}/role-mappings/realm"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(ROLE_LIST));
        return roles == null ? List.of() : roles;
    }

    public void addUserRealmRoles(String userId, List<KeycloakRole> roles) {
        if (roles.isEmpty()) {
            return;
        }
        execute(token -> restClient.post()
                .uri(properties.adminUrl("/users/{id}/role-mappings/realm"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    public void removeUserRealmRoles(String userId, List<KeycloakRole> roles) {
        if (roles.isEmpty()) {
            return;
        }
        execute(token -> restClient.method(HttpMethod.DELETE)
                .uri(properties.adminUrl("/users/{id}/role-mappings/realm"), properties.getRealm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    // ===== client role (permission) và composite realm role =====

    /** Tìm client theo clientId (tên), trả về null nếu không có. Dùng để lấy UUID nội bộ của client. */
    public KeycloakClient findClientByClientId(String clientId) {
        List<KeycloakClient> clients = execute(token -> restClient.get()
                .uri(properties.adminUrl("/clients?clientId={clientId}"), properties.getRealm(), clientId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(CLIENT_LIST));
        if (clients == null) {
            return null;
        }
        return clients.stream()
                .filter(client -> clientId.equals(client.clientId()))
                .findFirst()
                .orElse(null);
    }

    /** Toàn bộ client role của một client (tự phân trang). */
    public List<KeycloakRole> listClientRoles(String clientUuid) {
        List<KeycloakRole> result = new ArrayList<>();
        int first = 0;
        while (true) {
            int offset = first;
            List<KeycloakRole> page = execute(token -> restClient.get()
                    .uri(properties.adminUrl("/clients/{client}/roles?first={first}&max={max}"),
                            properties.getRealm(), clientUuid, offset, ROLE_PAGE_SIZE)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(ROLE_LIST));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < ROLE_PAGE_SIZE) {
                break;
            }
            first += ROLE_PAGE_SIZE;
        }
        return result;
    }

    /** Client role theo tên; Keycloak trả 404 (KeycloakAdminException status 404) nếu không có. */
    public KeycloakRole getClientRole(String clientUuid, String roleName) {
        return execute(token -> restClient.get()
                .uri(properties.adminUrl("/clients/{client}/roles/{role}"), properties.getRealm(), clientUuid, roleName)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(KeycloakRole.class));
    }

    /** Tạo client role; trùng tên thì Keycloak trả 409. */
    public void createClientRole(String clientUuid, KeycloakRole role) {
        execute(token -> restClient.post()
                .uri(properties.adminUrl("/clients/{client}/roles"), properties.getRealm(), clientUuid)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(role)
                .retrieve()
                .toBodilessEntity());
    }

    public void updateClientRole(String clientUuid, String roleName, KeycloakRole role) {
        execute(token -> restClient.put()
                .uri(properties.adminUrl("/clients/{client}/roles/{role}"), properties.getRealm(), clientUuid, roleName)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(role)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteClientRole(String clientUuid, String roleName) {
        execute(token -> restClient.delete()
                .uri(properties.adminUrl("/clients/{client}/roles/{role}"), properties.getRealm(), clientUuid, roleName)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
    }

    /** User được gán trực tiếp client role này (không tính kế thừa qua composite). */
    public List<KeycloakUser> listUsersInClientRole(String clientUuid, String roleName) {
        List<KeycloakUser> result = new ArrayList<>();
        int first = 0;
        while (true) {
            int offset = first;
            List<KeycloakUser> page = execute(token -> restClient.get()
                    .uri(properties.adminUrl("/clients/{client}/roles/{role}/users?first={first}&max={max}"),
                            properties.getRealm(), clientUuid, roleName, offset, ROLE_MEMBER_PAGE_SIZE)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(USER_LIST));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < ROLE_MEMBER_PAGE_SIZE) {
                break;
            }
            first += ROLE_MEMBER_PAGE_SIZE;
        }
        return result;
    }

    /** Các client role (của một client) nằm trong composite của realm role. */
    public List<KeycloakRole> getRealmRoleClientComposites(String realmRoleName, String clientUuid) {
        List<KeycloakRole> roles = execute(token -> restClient.get()
                .uri(properties.adminUrl("/roles/{role}/composites/clients/{client}"),
                        properties.getRealm(), realmRoleName, clientUuid)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(ROLE_LIST));
        return roles == null ? List.of() : roles;
    }

    public void addRealmRoleComposites(String realmRoleName, List<KeycloakRole> roles) {
        if (roles.isEmpty()) {
            return;
        }
        execute(token -> restClient.post()
                .uri(properties.adminUrl("/roles/{role}/composites"), properties.getRealm(), realmRoleName)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    public void removeRealmRoleComposites(String realmRoleName, List<KeycloakRole> roles) {
        if (roles.isEmpty()) {
            return;
        }
        execute(token -> restClient.method(HttpMethod.DELETE)
                .uri(properties.adminUrl("/roles/{role}/composites"), properties.getRealm(), realmRoleName)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    /** Client role hiệu lực của user (gồm kế thừa qua composite realm role) đối với một client. */
    public List<KeycloakRole> getUserEffectiveClientRoles(String userId, String clientUuid) {
        List<KeycloakRole> roles = execute(token -> restClient.get()
                .uri(properties.adminUrl("/users/{id}/role-mappings/clients/{client}/composite"),
                        properties.getRealm(), userId, clientUuid)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(ROLE_LIST));
        return roles == null ? List.of() : roles;
    }

    // ===== token + xử lý lỗi =====

    private <T> T execute(Function<String, T> call) {
        try {
            return call(call, false);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 401) {
                throw translate(ex);
            }
            // Token có thể đã bị thu hồi phía Keycloak: xin lại và thử đúng một lần.
            try {
                return call(call, true);
            } catch (RestClientResponseException retryEx) {
                throw translate(retryEx);
            } catch (ResourceAccessException retryEx) {
                throw unreachable(retryEx);
            }
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }
    }

    private <T> T call(Function<String, T> call, boolean forceNewToken) {
        return call.apply(accessToken(forceNewToken));
    }

    synchronized String accessToken(boolean forceNew) {
        if (!properties.isConfigured()) {
            throw new KeycloakAdminException(KeycloakAdminException.STATUS_NOT_CONFIGURED,
                    "Chưa cấu hình idp.url / idp.admin-client-id / idp.admin-client-secret");
        }
        Instant now = clock.instant();
        if (!forceNew && cachedToken != null && now.isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(properties.tokenUrl(), properties.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            // Không đưa body lỗi vào message để tránh lộ thông tin client.
            log.error("Keycloak admin token request failed: status={}", ex.getStatusCode().value());
            throw new KeycloakAdminException(401, "Không lấy được token service account của Keycloak", ex);
        } catch (ResourceAccessException ex) {
            throw unreachable(ex);
        }

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new KeycloakAdminException(401, "Keycloak không trả về access token");
        }
        cachedToken = response.accessToken();
        long ttl = Math.max(0, response.expiresIn() - EXPIRY_SKEW_SECONDS);
        cachedTokenExpiresAt = now.plusSeconds(ttl);
        return cachedToken;
    }

    private KeycloakAdminException translate(RestClientResponseException ex) {
        return new KeycloakAdminException(ex.getStatusCode().value(), extractErrorMessage(ex), ex);
    }

    private KeycloakAdminException unreachable(ResourceAccessException ex) {
        log.error("Keycloak unreachable: {}", ex.getMessage());
        return new KeycloakAdminException(KeycloakAdminException.STATUS_UNREACHABLE,
                "Không kết nối được Keycloak", ex);
    }

    /** Keycloak trả lỗi dạng {"errorMessage": "..."} hoặc {"error": "...", "error_description": "..."}. */
    private String extractErrorMessage(RestClientResponseException ex) {
        try {
            Map<String, Object> body = ex.getResponseBodyAs(JSON_MAP);
            if (body != null) {
                for (String key : List.of("errorMessage", "error_description", "error")) {
                    Object value = body.get(key);
                    if (value != null && StringUtils.hasText(value.toString())) {
                        return value.toString();
                    }
                }
            }
        } catch (Exception ignored) {
            // body không phải JSON
        }
        return "Keycloak trả về lỗi HTTP " + ex.getStatusCode().value();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
