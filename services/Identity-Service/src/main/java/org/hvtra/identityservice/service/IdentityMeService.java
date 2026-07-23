package org.hvtra.identityservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.identityservice.model.entity.AppUsers;
import org.hvtra.identityservice.model.enums.IdentityStatus;
import org.hvtra.identityservice.model.response.IdentityMeResponse;
import org.hvtra.identityservice.repository.IdentityUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdentityMeService {

    private static final Set<String> IGNORED_ROLES = Set.of(
            "default-roles-hvt",
            "offline_access",
            "uma_authorization"
    );

    private final IdentityUserRepository appUsersRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApiResponse<IdentityMeResponse> getMe(
            InternalUserPrincipal currentUser,
            String authorization
    ) {
        JwtUserContext jwtUser = parseAuthorization(authorization);

        if (!currentUser.userId().equals(jwtUser.subject())) {
            throw new AccessDeniedException("Invalid internal user context");
        }

        AppUsers user = appUsersRepository
                .findByIssuerAndKeycloakSubject(
                        jwtUser.issuer(),
                        jwtUser.subject()
                )
                .orElseGet(() -> AppUsers.builder()
                        .issuer(jwtUser.issuer())
                        .keycloakSubject(jwtUser.subject())
                        .status(IdentityStatus.ACTIVE)
                        .build());

        user.syncSnapshot(
                jwtUser.username(),
                jwtUser.email(),
                jwtUser.fullName(),
                jwtUser.emailVerified()
        );

//        if (!user.emailVerified()) {
//            throw new AccessDeniedException("Account is not active");
//        }

        AppUsers saved = appUsersRepository.save(user);

        return ApiResponse.<IdentityMeResponse>builder().data(IdentityMeResponse.builder()
                .id(saved.getId())
                .keycloakSubject(saved.getKeycloakSubject())
                .issuer(saved.getIssuer())
                .usernameSnapshot(saved.getUsernameSnapshot())
                .emailSnapshot(saved.getEmailSnapshot())
                .fullNameSnapshot(saved.getFullNameSnapshot())
                .emailVerified(saved.getEmailVerified())
                .status(saved.getStatus())
                .lastAuthenticatedAt(saved.getLastAuthenticatedAt())
                .roles(jwtUser.roles())
                .build())
                .code(200)
                .message("Success")
                .build();
    }

    private JwtUserContext parseAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token");
        }

        try {
            String token = authorization.substring("Bearer ".length()).trim();
            String[] parts = token.split("\\.");

            if (parts.length < 2) {
                throw new AccessDeniedException("Invalid bearer token");
            }

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            Map<String, Object> claims = objectMapper.readValue(
                    payloadJson,
                    new TypeReference<>() {
                    }
            );

            return new JwtUserContext(
                    stringClaim(claims, "sub"),
                    stringClaim(claims, "iss"),
                    stringClaim(claims, "preferred_username"),
                    stringClaim(claims, "email"),
                    stringClaim(claims, "name"),
                    booleanClaim(claims, "email_verified"),
                    rolesClaim(claims)
            );
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AccessDeniedException("Cannot parse bearer token", ex);
        }
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? "" : value.toString();
    }

    private Boolean booleanClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private Set<String> rolesClaim(Map<String, Object> claims) {
        Object realmAccessValue = claims.get("realm_access");

        if (!(realmAccessValue instanceof Map<?, ?> realmAccess)) {
            return Collections.emptySet();
        }

        Object rolesValue = realmAccess.get("roles");

        if (!(rolesValue instanceof Collection<?> roles)) {
            return Collections.emptySet();
        }

        return roles.stream()
                .map(Object::toString)
                .filter(role -> !role.isBlank())
                .filter(role -> !IGNORED_ROLES.contains(role))
                .collect(Collectors.toUnmodifiableSet());
    }

    private record JwtUserContext(
            String subject,
            String issuer,
            String username,
            String email,
            String fullName,
            Boolean emailVerified,
            Set<String> roles
    ) {
    }
}
