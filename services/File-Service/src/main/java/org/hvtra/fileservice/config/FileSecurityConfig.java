package org.hvtra.fileservice.config;

import jakarta.annotation.PostConstruct;
import org.hvtra.commonservice.common_core.config.BaseSecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
public class FileSecurityConfig extends BaseSecurityConfig {
    @Value("${security.internal.header-secret}")
    private String configuredSecret;

    @PostConstruct
    void validateSecret() {
        if (configuredSecret == null || configuredSecret.isBlank()
                || configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || "dev-internal-header-secret".equals(configuredSecret)) {
            throw new IllegalStateException("Explicit strong internal authentication secret required");
        }
    }

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return super.securityFilterChain(http);
    }
}
