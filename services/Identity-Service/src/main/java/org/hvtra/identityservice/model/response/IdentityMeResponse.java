package org.hvtra.identityservice.model.response;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.identityservice.model.enums.IdentityStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level =  AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class IdentityMeResponse {
    UUID id;

    String keycloakSubject;

    @Column(nullable = false, length = 255)
    String issuer;

    @Column(name = "username_snapshot", length = 255)
    private String usernameSnapshot;

    @Column(name = "email_snapshot", length = 320)
    private String emailSnapshot;

    @Column(name = "full_name_snapshot", length = 255)
    private String fullNameSnapshot;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdentityStatus status = IdentityStatus.ACTIVE;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    private Set<String> roles;
}
