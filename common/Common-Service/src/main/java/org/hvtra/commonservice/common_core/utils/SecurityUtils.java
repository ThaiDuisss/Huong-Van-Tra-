package org.hvtra.commonservice.common_core.utils;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Authentication authentication() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated");
        }

        return authentication;
    }

    public static InternalUserPrincipal currentUser() {
        Object principal = authentication().getPrincipal();

        if (!(principal instanceof InternalUserPrincipal internalUser)) {
            throw new AccessDeniedException("Invalid internal user principal");
        }

        return internalUser;
    }

    public static String currentUserId() {
        return currentUser().userId();
    }

    public static String currentUsername() {
        return currentUser().username();
    }


    public static Set<String> currentRoles() {
        return currentUser().roles();
    }



    public static String currentCorrelationId() {
        return currentUser().correlationId();
    }

    public static boolean hasRole(String role) {
        String normalizedRole = role.startsWith("ROLE_")
                ? role
                : "ROLE_" + role;

        return authentication().getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals(normalizedRole)
                );
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isStaff() {
        return hasRole("STAFF");
    }

    public static boolean isCustomer() {
        return hasRole("CUSTOMER");
    }
}