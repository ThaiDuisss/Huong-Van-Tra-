package org.hvtra.identityservice.model.request;

import lombok.*;

import java.util.List;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserCreation {
        String username;
        boolean enabled;
        String email;
        boolean emailVerified;
        String firstName;
        String lastName;
        List<Credential> credentials;

    public static record Credential(String type, String value, boolean temporary) {

    }
}
