package org.hvtra.iventoryservice.config;

import org.hvtra.commonservice.common_core.config.BaseSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class InventorySecurityConfig extends BaseSecurityConfig {

    /** Role được duyệt phiếu kho — khớp với isAdmin()/isManager() ở FE. */
    private static final String[] APPROVER_ROLES = {"ADMIN", "MANAGER"};

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
    }
}
