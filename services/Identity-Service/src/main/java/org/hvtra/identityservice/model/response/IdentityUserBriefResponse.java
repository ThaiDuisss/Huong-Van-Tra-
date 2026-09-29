package org.hvtra.identityservice.model.response;

public record IdentityUserBriefResponse(
        String keycloakSubject,
        String fullName,
        String username,
        String email
) {
}
