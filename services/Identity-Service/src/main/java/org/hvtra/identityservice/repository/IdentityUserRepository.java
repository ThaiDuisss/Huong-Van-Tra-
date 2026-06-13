package org.hvtra.identityservice.repository;

import org.hvtra.identityservice.model.entity.AppUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface IdentityUserRepository
        extends JpaRepository<AppUsers, UUID> {

    Optional<AppUsers> findByIssuerAndKeycloakSubject(
            String issuer,
            String keycloakSubject
    );
}