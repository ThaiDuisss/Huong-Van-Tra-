package org.hvtra.identityservice.account.client;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UserRepresentation của Keycloak. Các trường không khai báo (attributes, requiredActions, ...)
 * được giữ nguyên trong {@code other} để khi PUT lại không làm mất dữ liệu của user.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeycloakUser {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private Long createdTimestamp;
    private List<Credential> credentials;

    @JsonIgnore
    private Map<String, Object> other = new LinkedHashMap<>();

    @JsonAnySetter
    public void putOther(String name, Object value) {
        other.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getOther() {
        return other;
    }

    public record Credential(String type, String value, boolean temporary) {
        public static Credential temporaryPassword(String password) {
            return new Credential("password", password, true);
        }

        /** Không in mật khẩu khi object bị log. */
        @Override
        public String toString() {
            return "Credential{type=" + type + ", temporary=" + temporary + "}";
        }
    }
}
