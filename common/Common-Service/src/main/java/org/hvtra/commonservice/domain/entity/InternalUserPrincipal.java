package org.hvtra.commonservice.domain.entity;

import java.util.Set;

public record InternalUserPrincipal(
        String userId,
        String username,
        String issuer,
        Set<String> roles,
        String correlationId
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}