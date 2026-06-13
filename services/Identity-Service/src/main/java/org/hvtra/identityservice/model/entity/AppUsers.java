package org.hvtra.identityservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Where;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.identityservice.model.enums.IdentityStatus;

import java.time.Instant;

@Entity
@Getter
@Setter
@FieldDefaults(level =  AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "app_users",
uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_identity_users_issuer_subject",
                columnNames = {
                        "issuer",
                        "keycloak_subject"}
        )
})
@Where(clause = "is_deleted=false")
public class AppUsers extends BaseEntity {
    @Column(
            name = "keycloak_subject",
            nullable = false,
            length = 255
    )
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


}
