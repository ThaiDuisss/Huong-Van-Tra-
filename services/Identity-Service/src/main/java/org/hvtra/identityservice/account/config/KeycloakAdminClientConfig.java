package org.hvtra.identityservice.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class KeycloakAdminClientConfig {

    /**
     * Timeout tường minh cho mọi lời gọi Keycloak. Ngưỡng slow-call của circuit breaker {@code keycloak}
     * (application.yml) phải thấp hơn read timeout.
     */
    @Bean
    public RestClient keycloakAdminRestClient(
            @Value("${idp.admin-connect-timeout:5s}") Duration connectTimeout,
            @Value("${idp.admin-read-timeout:15s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
