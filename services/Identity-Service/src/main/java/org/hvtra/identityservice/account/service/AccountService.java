package org.hvtra.identityservice.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.identityservice.account.client.KeycloakAdminClient;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakRole;
import org.hvtra.identityservice.account.client.KeycloakUser;
import org.hvtra.identityservice.account.dto.AccountResponse;
import org.hvtra.identityservice.account.dto.AccountSearchRequest;
import org.hvtra.identityservice.account.dto.CreateAccountRequest;
import org.hvtra.identityservice.account.dto.UpdateAccountRequest;
import org.hvtra.identityservice.account.model.StaffRole;
import org.hvtra.identityservice.actionlog.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Quản lý tài khoản nhân viên. Keycloak là nguồn dữ liệu gốc: mọi thao tác đi qua Keycloak Admin API,
 * không ghi vào bảng app_users (snapshot ở đó tự đồng bộ khi người dùng đăng nhập).
 * <p>
 * "Tài khoản nhân viên" = user Keycloak có ít nhất một realm role thuộc {@link StaffRole} (hoặc SUPER_ADMIN,
 * chỉ để xem). Tài khoản khách hàng không bao giờ lộ ra qua service này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_PAGE_SIZE = 100;

    /** Thứ tự hiển thị vai trò: SUPER_ADMIN trước, sau đó theo thứ tự khai báo của StaffRole. */
    private static final List<String> ROLE_ORDER = buildRoleOrder();
    private static final Comparator<String> ROLE_COMPARATOR = Comparator.comparingInt(ROLE_ORDER::indexOf);

    private final KeycloakAdminClient keycloak;

    public PageResponse<AccountResponse> search(AccountSearchRequest request) {
        StaffRole roleFilter = parseRoleFilter(request.getRole());
        String keyword = normalize(request.getKeyword());
        Boolean enabledFilter = request.getEnabled();
        int size = Math.min(Math.max(request.getSize(), 1), MAX_PAGE_SIZE);
        int page = Math.max(request.getPage(), 0);

        List<AccountResponse> matched = withKeycloak(() -> loadStaffAccounts().stream()
                .filter(account -> roleFilter == null || account.roles().contains(roleFilter.name()))
                .filter(account -> enabledFilter == null || account.enabled() == enabledFilter)
                .filter(account -> matchesKeyword(account, keyword))
                .sorted(Comparator.comparing(
                        (AccountResponse account) -> normalize(account.username()))
                        .thenComparing(AccountResponse::id))
                .toList());

        int total = matched.size();
        long offset = (long) page * size;
        List<AccountResponse> content = offset >= total
                ? List.of()
                : matched.subList((int) offset, (int) Math.min(offset + size, total));
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    /** Các vai trò có thể gán, chỉ gồm những vai trò thật sự tồn tại trong realm. */
    public List<String> assignableRoles() {
        Map<String, List<KeycloakRole>> index = withKeycloak(this::realmRoleIndex);
        return StaffRole.names().stream()
                .filter(index::containsKey)
                .toList();
    }

    public AccountResponse get(String id) {
        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(id);
            Set<String> roles = canonicalRoles(keycloak.getUserRealmRoles(id));
            requireStaffAccount(roles);
            return toResponse(user, roles);
        });
    }

    public AccountResponse create(CreateAccountRequest request) {
        Set<StaffRole> roles = parseAssignableRoles(request.roles());

        return withKeycloak(() -> {
            Map<String, List<KeycloakRole>> index = realmRoleIndex();
            List<KeycloakRole> toAssign = roles.stream().map(role -> realmRoleFor(index, role)).toList();

            KeycloakUser user = new KeycloakUser();
            user.setUsername(request.username().trim());
            user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
            user.setEnabled(true);
            // Tài khoản do quản trị viên tạo: coi email là đã xác minh để không bị chặn bởi bước verify-email.
            user.setEmailVerified(true);
            user.setCredentials(List.of(KeycloakUser.Credential.temporaryPassword(request.password())));

            String id = keycloak.createUser(user);
            try {
                keycloak.addUserRealmRoles(id, toAssign);
            } catch (KeycloakAdminException ex) {
                // Không để lại user "mồ côi" không có vai trò (sẽ không hiện trong danh sách nhân viên).
                rollbackCreatedUser(id);
                throw ex;
            }

            KeycloakUser created = keycloak.getUser(id);
            return toResponse(created, roleNames(roles));
        });
    }

    public AccountResponse update(String id, UpdateAccountRequest request) {
        Set<StaffRole> desired = parseAssignableRoles(request.roles());

        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(id);
            List<KeycloakRole> currentRealmRoles = keycloak.getUserRealmRoles(id);
            requireManageable(canonicalRoles(currentRealmRoles));

            Map<String, List<KeycloakRole>> index = realmRoleIndex();
            Set<String> desiredNames = roleNames(desired);
            Set<String> currentNames = canonicalRoles(currentRealmRoles);

            List<KeycloakRole> toAdd = desired.stream()
                    .filter(role -> !currentNames.contains(role.name()))
                    .map(role -> realmRoleFor(index, role))
                    .toList();
            // Chỉ gỡ các role nhân viên; role khác của user (default-roles, CUSTOMER, ...) giữ nguyên.
            List<KeycloakRole> toRemove = currentRealmRoles.stream()
                    .filter(role -> StaffRole.from(role.name()).isPresent())
                    .filter(role -> !desiredNames.contains(StaffRole.canonicalName(role.name())))
                    .toList();

            user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
            keycloak.updateUser(id, user);
            // Thêm trước, gỡ sau: nếu lỗi giữa chừng tài khoản vẫn còn ít nhất một vai trò nhân viên.
            keycloak.addUserRealmRoles(id, toAdd);
            keycloak.removeUserRealmRoles(id, toRemove);

            return toResponse(user, desiredNames);
        });
    }

    public AccountResponse changeStatus(String id, boolean enabled, String currentUserId) {
        if (!enabled && id != null && id.equals(currentUserId)) {
            throw new BusinessException("ACCOUNT_SELF_DISABLE", "Bạn không thể tự khóa tài khoản của chính mình");
        }

        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(id);
            Set<String> roles = canonicalRoles(keycloak.getUserRealmRoles(id));
            requireManageable(roles);

            user.setEnabled(enabled);
            keycloak.updateUser(id, user);
            if (!enabled) {
                endSessionsQuietly(id);
            }
            return toResponse(user, roles);
        });
    }

    /**
     * Admin đặt lại mật khẩu cho nhân viên. Mật khẩu mới là mật khẩu tạm: nhân viên phải tự đổi ở lần đăng nhập
     * kế tiếp. Mọi phiên đang mở của tài khoản bị kết thúc để mật khẩu cũ không còn dùng được.
     */
    public AccountResponse resetPassword(String id, String newPassword) {
        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(id);
            Set<String> roles = canonicalRoles(keycloak.getUserRealmRoles(id));
            requireManageable(roles);

            keycloak.resetPassword(id, KeycloakUser.Credential.temporaryPassword(newPassword));
            endSessionsQuietly(id);
            return toResponse(user, roles);
        });
    }

    // ===== helpers =====

    /** Gom thành viên của từng realm role nhân viên; mỗi user xuất hiện một lần với đủ vai trò. */
    private Collection<AccountResponse> loadStaffAccounts() {
        Map<String, List<KeycloakRole>> index = realmRoleIndex();
        Map<String, KeycloakUser> users = new LinkedHashMap<>();
        Map<String, Set<String>> rolesByUser = new LinkedHashMap<>();

        for (String canonical : ROLE_ORDER) {
            for (KeycloakRole realmRole : index.getOrDefault(canonical, List.of())) {
                for (KeycloakUser user : keycloak.listUsersInRole(realmRole.name())) {
                    users.putIfAbsent(user.getId(), user);
                    rolesByUser.computeIfAbsent(user.getId(), key -> new TreeSet<>(ROLE_COMPARATOR)).add(canonical);
                }
            }
        }

        List<AccountResponse> accounts = new ArrayList<>(users.size());
        users.forEach((id, user) -> accounts.add(toResponse(user, rolesByUser.get(id))));
        return accounts;
    }

    /** canonical role (ADMIN, ..., SUPER_ADMIN) -> các realm role tương ứng trong Keycloak (không phân biệt hoa thường/alias). */
    private Map<String, List<KeycloakRole>> realmRoleIndex() {
        Map<String, List<KeycloakRole>> index = new LinkedHashMap<>();
        for (KeycloakRole role : keycloak.listRealmRoles()) {
            String canonical = StaffRole.canonicalName(role.name());
            if (ROLE_ORDER.contains(canonical)) {
                index.computeIfAbsent(canonical, key -> new ArrayList<>()).add(role);
            }
        }
        return index;
    }

    /** Realm role dùng để gán cho một StaffRole: ưu tiên role trùng tên chuẩn, nếu không lấy alias đầu tiên. */
    private KeycloakRole realmRoleFor(Map<String, List<KeycloakRole>> index, StaffRole role) {
        List<KeycloakRole> candidates = index.getOrDefault(role.name(), List.of());
        if (candidates.isEmpty()) {
            throw new BusinessException("ACCOUNT_ROLE_NOT_FOUND",
                    "Vai trò " + role.name() + " chưa được tạo trong Keycloak");
        }
        return candidates.stream()
                .filter(candidate -> stripPrefix(candidate.name()).equalsIgnoreCase(role.name()))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private Set<String> canonicalRoles(List<KeycloakRole> realmRoles) {
        Set<String> roles = new TreeSet<>(ROLE_COMPARATOR);
        for (KeycloakRole role : realmRoles) {
            String canonical = StaffRole.canonicalName(role.name());
            if (ROLE_ORDER.contains(canonical)) {
                roles.add(canonical);
            }
        }
        return roles;
    }

    private Set<StaffRole> parseAssignableRoles(List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            throw new BusinessException("ACCOUNT_ROLE_REQUIRED", "Phải chọn ít nhất một vai trò");
        }
        Set<StaffRole> roles = EnumSet.noneOf(StaffRole.class);
        for (String raw : rawRoles) {
            roles.add(StaffRole.from(raw).orElseThrow(() -> new BusinessException(
                    "ACCOUNT_ROLE_NOT_ALLOWED",
                    "Vai trò không hợp lệ hoặc không được phép gán: " + raw)));
        }
        return roles;
    }

    private StaffRole parseRoleFilter(String rawRole) {
        if (!StringUtils.hasText(rawRole)) {
            return null;
        }
        return StaffRole.from(rawRole).orElseThrow(() -> new BusinessException(
                "ACCOUNT_ROLE_NOT_ALLOWED", "Vai trò lọc không hợp lệ: " + rawRole));
    }

    private Set<String> roleNames(Set<StaffRole> roles) {
        Set<String> names = new TreeSet<>(ROLE_COMPARATOR);
        roles.forEach(role -> names.add(role.name()));
        return names;
    }

    /** Tài khoản không có vai trò nhân viên nào (vd. khách hàng) coi như không tồn tại với API này. */
    private void requireStaffAccount(Set<String> roles) {
        if (roles.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy tài khoản nhân viên");
        }
    }

    private void requireManageable(Set<String> roles) {
        requireStaffAccount(roles);
        if (roles.contains(StaffRole.SUPER_ADMIN)) {
            throw new BusinessException("ACCOUNT_PROTECTED",
                    "Không thể chỉnh sửa, khóa hoặc đặt lại mật khẩu tài khoản SUPER_ADMIN", HttpStatus.FORBIDDEN);
        }
    }

    private void rollbackCreatedUser(String id) {
        try {
            keycloak.deleteUser(id);
        } catch (RuntimeException rollbackEx) {
            log.error("Cannot roll back Keycloak user {} after role assignment failure: {}", id, rollbackEx.getMessage());
        }
    }

    private void endSessionsQuietly(String id) {
        try {
            keycloak.logoutUser(id);
        } catch (RuntimeException ex) {
            log.warn("Sessions of account {} could not be ended: {}", id, ex.getMessage());
        }
    }

    private boolean matchesKeyword(AccountResponse account, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return normalize(account.username()).contains(keyword)
                || normalize(account.email()).contains(keyword)
                || normalize(account.fullName()).contains(keyword)
                || normalize(join(account.firstName(), account.lastName())).contains(keyword);
    }

    private AccountResponse toResponse(KeycloakUser user, Set<String> roles) {
        return new AccountResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                // Họ tên kiểu Việt: họ (lastName) đứng trước tên (firstName).
                join(user.getLastName(), user.getFirstName()),
                !Boolean.FALSE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getEmailVerified()),
                List.copyOf(roles),
                user.getCreatedTimestamp() == null ? null : Instant.ofEpochMilli(user.getCreatedTimestamp()),
                !roles.contains(StaffRole.SUPER_ADMIN)
        );
    }

    private <T> T withKeycloak(Supplier<T> action) {
        try {
            return action.get();
        } catch (KeycloakAdminException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(KeycloakAdminException ex) {
        String detail = ex.getMessage() == null ? "" : ex.getMessage();
        return switch (ex.getStatus()) {
            case 404 -> new ResourceNotFoundException("Không tìm thấy tài khoản");
            case 409 -> new BusinessException("ACCOUNT_ALREADY_EXISTS", conflictMessage(detail), HttpStatus.CONFLICT);
            case 400 -> new BusinessException("ACCOUNT_INVALID", "Keycloak từ chối dữ liệu tài khoản: " + detail);
            case 401, 403 -> {
                log.error("Keycloak admin API rejected the service account: status={}", ex.getStatus());
                yield new BusinessException("KEYCLOAK_ADMIN_FORBIDDEN",
                        "Service account của Identity-Service chưa đủ quyền quản lý người dùng trên Keycloak",
                        HttpStatus.BAD_GATEWAY);
            }
            case KeycloakAdminException.STATUS_NOT_CONFIGURED -> new BusinessException(
                    "KEYCLOAK_ADMIN_NOT_CONFIGURED",
                    "Chức năng quản lý tài khoản chưa được cấu hình kết nối Keycloak",
                    HttpStatus.SERVICE_UNAVAILABLE);
            case KeycloakAdminException.STATUS_UNREACHABLE -> new BusinessException(
                    "KEYCLOAK_UNAVAILABLE", "Không kết nối được Keycloak, vui lòng thử lại sau",
                    HttpStatus.SERVICE_UNAVAILABLE);
            default -> {
                log.error("Keycloak admin API error: status={} message={}", ex.getStatus(), detail);
                yield new BusinessException("KEYCLOAK_ERROR", "Keycloak gặp lỗi khi xử lý yêu cầu",
                        HttpStatus.BAD_GATEWAY);
            }
        };
    }

    private String conflictMessage(String keycloakMessage) {
        String lower = keycloakMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("email")) {
            return "Email đã được sử dụng bởi tài khoản khác";
        }
        if (lower.contains("username")) {
            return "Tên đăng nhập đã tồn tại";
        }
        return "Tên đăng nhập hoặc email đã tồn tại";
    }

    private static List<String> buildRoleOrder() {
        List<String> order = new ArrayList<>();
        order.add(StaffRole.SUPER_ADMIN);
        order.addAll(StaffRole.names());
        return List.copyOf(order);
    }

    private static String stripPrefix(String role) {
        return role.regionMatches(true, 0, "ROLE_", 0, 5) ? role.substring(5) : role;
    }

    private static String join(String first, String second) {
        return ((first == null ? "" : first.trim()) + " " + (second == null ? "" : second.trim())).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
