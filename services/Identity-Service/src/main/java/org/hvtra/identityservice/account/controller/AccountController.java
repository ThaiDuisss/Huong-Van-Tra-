package org.hvtra.identityservice.account.controller;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.constants.ErrorEnum;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.identityservice.account.dto.AccountResponse;
import org.hvtra.identityservice.account.dto.AccountSearchRequest;
import org.hvtra.identityservice.account.dto.ChangeAccountStatusRequest;
import org.hvtra.identityservice.account.dto.CreateAccountRequest;
import org.hvtra.identityservice.account.dto.ResetPasswordRequest;
import org.hvtra.identityservice.account.dto.UpdateAccountRequest;
import org.hvtra.identityservice.account.service.AccountService;
import org.hvtra.identityservice.actionlog.dto.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản lý tài khoản nhân viên (Keycloak): tạo, sửa, khóa và đặt lại mật khẩu. Dành cho SUPER_ADMIN.
 * Ví dụ: GET /api/identity/accounts?keyword=lan&role=SALE_STAFF&enabled=true&page=0&size=20
 */
@Slf4j
@RestController
@RequestMapping("/api/identity/accounts")
@RequiredArgsConstructor
public class AccountController {


    private final AccountService accountService;

    @GetMapping
    public ApiResponse<PageResponse<AccountResponse>> search(@ModelAttribute AccountSearchRequest request) {
        requireAccountAdmin();
        return ApiResponse.success(accountService.search(request));
    }

    /** Danh sách vai trò có thể gán (đã tồn tại trong realm). */
    @GetMapping("/roles")
    public ApiResponse<List<String>> assignableRoles() {
        requireAccountAdmin();
        return ApiResponse.success(accountService.assignableRoles());
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> detail(@PathVariable String id) {
        requireAccountAdmin();
        return ApiResponse.success(accountService.get(id));
    }

    @PostMapping
    @UserAction(value = "ACCOUNT_CREATE", entityType = "Account", entityId = "#result.data.id",
            detail = "#request.username")
    public ApiResponse<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        requireAccountAdmin();
        return ApiResponse.created("Tạo tài khoản thành công", accountService.create(request));
    }

    @PutMapping("/{id}")
    @UserAction(value = "ACCOUNT_UPDATE", entityType = "Account", entityId = "#id",
            detail = "'roles=' + #request.roles")
    public ApiResponse<AccountResponse> update(@PathVariable String id,
                                               @Valid @RequestBody UpdateAccountRequest request) {
        requireAccountAdmin();
        return ApiResponse.updated("Cập nhật tài khoản thành công", accountService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @UserAction(value = "ACCOUNT_STATUS_CHANGE", entityType = "Account", entityId = "#id",
            detail = "'enabled=' + #request.enabled")
    public ApiResponse<AccountResponse> changeStatus(@PathVariable String id,
                                                     @Valid @RequestBody ChangeAccountStatusRequest request,
                                                     @AuthenticationPrincipal InternalUserPrincipal currentUser) {
        requireAccountAdmin();
        String currentUserId = currentUser == null ? null : currentUser.userId();
        AccountResponse account = accountService.changeStatus(id, request.enabled(), currentUserId);
        return ApiResponse.updated(
                request.enabled() ? "Đã kích hoạt tài khoản" : "Đã khóa tài khoản",
                account);
    }

    /** Mật khẩu không được ghi vào nhật ký hoạt động: chỉ ghi tài khoản nào bị đặt lại. */
    @PostMapping("/{id}/reset-password")
    @UserAction(value = "ACCOUNT_PASSWORD_RESET", entityType = "Account", entityId = "#id")
    public ApiResponse<AccountResponse> resetPassword(@PathVariable String id,
                                                      @Valid @RequestBody ResetPasswordRequest request) {
        requireAccountAdmin();
        return ApiResponse.updated("Đã đặt lại mật khẩu", accountService.resetPassword(id, request.password()));
    }

    private void requireAccountAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean allowed = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(Role.SUPER_ADMIN.authority()::equals);
            if (allowed) {
                return;
            }
            log.warn("Account management access denied. user={} authorities={}",
                    authentication.getName(), authentication.getAuthorities());
        }
        throw new AppException(ErrorEnum.FORBIDDEN);
    }
}
