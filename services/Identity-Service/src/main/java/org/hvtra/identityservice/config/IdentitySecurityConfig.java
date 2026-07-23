package org.hvtra.identityservice.config;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.config.BaseSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Slf4j
public class IdentitySecurityConfig extends BaseSecurityConfig {

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("IdentitySecurityConfig securityFilterChain");
        return super.securityFilterChain(http);
    }

    @Override
    protected String[] publicMatchers() {
        return new String[]{
                "/actuator/**",
                "/api/identity/v3/api-docs/**",
                "/api/identity/swagger-ui/**",
                "/api/identity/swagger-ui.html",
                "/api/identity/public/**"
        };
    }
}
