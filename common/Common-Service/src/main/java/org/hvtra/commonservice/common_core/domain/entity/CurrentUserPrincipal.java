package org.hvtra.commonservice.common_core.domain.entity;

import java.util.UUID;

public record CurrentUserPrincipal(UUID userId,
                                   String username,
                                   UUID branchId) {
}
