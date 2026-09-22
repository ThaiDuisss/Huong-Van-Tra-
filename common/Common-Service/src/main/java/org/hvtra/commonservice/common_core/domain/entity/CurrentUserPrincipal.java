package org.hvtra.commonservice.domain.entity;

import java.util.UUID;

public record CurrentUserPrincipal(UUID userId,
                                   String username,
                                   UUID branchId) {
}
