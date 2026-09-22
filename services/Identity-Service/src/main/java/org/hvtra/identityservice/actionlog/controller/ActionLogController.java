package org.hvtra.identityservice.actionlog.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.constants.ErrorEnum;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.identityservice.actionlog.dto.ActionLogResponse;
import org.hvtra.identityservice.actionlog.dto.ActionLogSearchRequest;
import org.hvtra.identityservice.actionlog.dto.PageResponse;
import org.hvtra.identityservice.actionlog.service.ActionLogService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Tra cứu hành động người dùng. Chỉ ADMIN hoặc SUPER_ADMIN.
 * Ví dụ: GET /api/identity/action-logs?userId=u1&action=ORDER_CANCEL&from=2026-09-01T00:00:00Z&page=0&size=20
 */
@Slf4j
@RestController
@RequestMapping("/api/identity/action-logs")
@RequiredArgsConstructor
public class ActionLogController {

    /** So khớp không phân biệt hoa/thường vì gateway giữ nguyên tên role của Keycloak (vd. "admin", "superadmin"). */
    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_SUPERADMIN");

    private final ActionLogService actionLogService;

    @GetMapping
    public ApiResponse<PageResponse<ActionLogResponse>> search(@ModelAttribute ActionLogSearchRequest request) {
        requireAdmin();
        return ApiResponse.success(actionLogService.search(request));
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean allowed = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority -> ALLOWED_ROLES.contains(authority.toUpperCase()));
            if (allowed) {
                return;
            }
            log.warn("Action log access denied. user={} authorities={}",
                    authentication.getName(), authentication.getAuthorities());
        }
        throw new AppException(ErrorEnum.FORBIDDEN);
    }
}
