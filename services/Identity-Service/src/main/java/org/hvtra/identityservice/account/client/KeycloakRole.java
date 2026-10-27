package org.hvtra.identityservice.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * RoleRepresentation rút gọn của Keycloak. Khi gán/gỡ role chỉ cần id + name; với permission (client role)
 * dùng thêm {@code description} và {@code attributes} (displayName) làm metadata, không cần bảng riêng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakRole(
        String id,
        String name,
        String description,
        Boolean composite,
        Boolean clientRole,
        Map<String, List<String>> attributes
) {
    public KeycloakRole(String id, String name) {
        this(id, name, null, null, null, null);
    }

    /** Giá trị đầu của một attribute (Keycloak lưu attribute dạng danh sách). */
    public String attribute(String key) {
        if (attributes == null) {
            return null;
        }
        List<String> values = attributes.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
