package org.hvtra.iventoryservice.config;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.config.BaseSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class InventorySecurityConfig extends BaseSecurityConfig {

    /** Role được duyệt phiếu kho — khớp với isSuperAdmin()/isManager() ở FE. */
    private static final String[] APPROVER_ROLES = Role.names(Role.SUPER_ADMIN, Role.MANAGER);
    /** Role được quản lý vị trí lưu kho. */
    private static final String[] SUPER_ADMIN_ROLES = Role.names(Role.SUPER_ADMIN);
    /** Role được lập yêu cầu chuyển kho lên kệ (thủ kho, và nhân viên bán tạo từ POS khi kệ thiếu hàng). */
    private static final String[] SHELF_REQUEST_ROLES =
            Role.names(Role.SUPER_ADMIN, Role.MANAGER, Role.INVENTORY_STAFF, Role.SALE_STAFF);

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return super.securityFilterChain(http);
    }

    @Override
    protected void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // Phiếu nhập NCC — duyệt / từ chối, duyệt ngoại lệ & ghi sổ, hủy phiếu: chỉ ADMIN và MANAGER
        auth.requestMatchers(HttpMethod.POST,
                        "/api/inventory/goods-receipts/*/approve",
                        "/api/inventory/goods-receipts/*/reject",
                        "/api/inventory/goods-receipts/*/post",
                        "/api/inventory/goods-receipts/*/cancel")
                .hasAnyRole(APPROVER_ROLES);

        // Phiếu nhập kệ (SC-05) — duyệt / từ chối / duyệt lại phiếu POS xuất ngay: chỉ ADMIN và MANAGER.
        // Người duyệt khác người tạo và hủy chỉ bởi người tạo / Admin: kiểm tra theo dữ liệu phiếu trong service.
        auth.requestMatchers(HttpMethod.POST,
                        "/api/inventory/shelf-replenishments/*/approve",
                        "/api/inventory/shelf-replenishments/*/reject",
                        "/api/inventory/shelf-replenishments/*/review")
                .hasAnyRole(APPROVER_ROLES);
        auth.requestMatchers(HttpMethod.POST,
                        "/api/inventory/shelf-replenishments",
                        "/api/inventory/shelf-replenishments/*/submit",
                        "/api/inventory/shelf-replenishments/*/cancel")
                .hasAnyRole(SHELF_REQUEST_ROLES);
        auth.requestMatchers(HttpMethod.PUT, "/api/inventory/shelf-replenishments/*")
                .hasAnyRole(SHELF_REQUEST_ROLES);

        // Quản lý vị trí lưu kho: chỉ SUPER_ADMIN
        auth.requestMatchers("/api/inventory/inventory-locations/all")
                .hasAnyRole(SUPER_ADMIN_ROLES);
    }
}
