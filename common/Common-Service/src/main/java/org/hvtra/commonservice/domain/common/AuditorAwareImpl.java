package org.hvtra.commonservice.domain.common;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {
    private final String SYSTEM_USER = "SYSTEM";
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM_USER);
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject())
                    .or(() -> Optional.of(SYSTEM_USER));
        }

        return Optional.ofNullable(authentication.getName())
                .or(() -> Optional.of(SYSTEM_USER));
    }
}
