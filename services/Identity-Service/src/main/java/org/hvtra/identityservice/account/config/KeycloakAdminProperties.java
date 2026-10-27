package org.hvtra.identityservice.account.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cấu hình gọi Keycloak Admin REST API bằng service account (client-credentials).
 * Giá trị secret chỉ đọc từ biến môi trường, không bao giờ được log ra.
 */
@Getter
@Component
public class KeycloakAdminProperties {

    private final String url;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    /** Client chứa các client role dùng làm permission (phải trùng với gateway: security.permission-client-id). */
    private final String permissionClientId;

    public KeycloakAdminProperties(String url, String realm, String clientId, String clientSecret) {
        this(url, realm, clientId, clientSecret, "hvtra-pos");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KeycloakAdminProperties(
            @Value("${idp.url:}") String url,
            @Value("${idp.realm:hvt}") String realm,
            @Value("${idp.admin-client-id:}") String clientId,
            @Value("${idp.admin-client-secret:}") String clientSecret,
            @Value("${idp.permission-client-id:hvtra-pos}") String permissionClientId
    ) {
        this.url = trimTrailingSlash(url);
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.permissionClientId = permissionClientId;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(url)
                && StringUtils.hasText(realm)
                && StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret);
    }

    public String tokenUrl() {
        return url + "/realms/{realm}/protocol/openid-connect/token";
    }

    public String adminUrl(String path) {
        return url + "/admin/realms/{realm}" + path;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Không in secret khi object bị log. */
    @Override
    public String toString() {
        return "KeycloakAdminProperties{url=" + url + ", realm=" + realm + ", clientId=" + clientId + "}";
    }
}
