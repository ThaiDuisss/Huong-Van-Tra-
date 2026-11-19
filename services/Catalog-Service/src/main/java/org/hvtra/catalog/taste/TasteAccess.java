package org.hvtra.catalog.taste;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

final class TasteAccess {
    private TasteAccess() {
    }

    static void readTaxonomy() {
        SecurityUtils.currentUser();
        if (!SecurityUtils.hasAnyRole(Role.SALE_STAFF, Role.QC, Role.INVENTORY_STAFF, Role.MANAGER, Role.SUPER_ADMIN)
                && !SecurityUtils.hasPermission("catalog:taste:read")) {
            throw new AccessDeniedException("Thiếu quyền đọc bộ tiêu chí khẩu vị");
        }
    }

    static UUID manage() {
        if (!isManager()) {
            throw new AccessDeniedException("Thiếu quyền quản lý khẩu vị");
        }
        return actor();
    }

    static UUID assess() {
        if (!isManager() && !SecurityUtils.hasPermission("catalog:taste:assess")) {
            throw new AccessDeniedException("Thiếu quyền đánh giá khẩu vị");
        }
        return actor();
    }

    static boolean isManager() {
        return SecurityUtils.hasAnyRole(Role.MANAGER, Role.SUPER_ADMIN);
    }

    private static UUID actor() {
        String value = SecurityUtils.currentUserId();
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // An authenticated writer must have a UUID actor identity.
        }
        throw new AccessDeniedException("Không xác định được nhân viên");
    }
}
