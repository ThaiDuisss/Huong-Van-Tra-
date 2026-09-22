package org.hvtra.commonservice.domain.common;

import org.hvtra.commonservice.utils.SecurityUtils;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

public class AuditorAwareImpl implements AuditorAware<UUID> {
    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            return parseUuid(SecurityUtils.currentUserId());
        } catch (AccessDeniedException ex) {
            return Optional.empty();
        }
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
