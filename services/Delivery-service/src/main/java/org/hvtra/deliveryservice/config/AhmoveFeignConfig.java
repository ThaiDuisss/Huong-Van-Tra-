package org.hvtra.deliveryservice.config;

import feign.RequestInterceptor;
import org.apache.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AhmoveFeignConfig {

    @Bean
    public RequestInterceptor AhmoveInterceptor(AhamoveProperties properties) {
        return requestTemplate ->  {
            String token = properties.token();

            requestTemplate.header(
                    HttpHeaders.AUTHORIZATION,
                    token
            );
        };
    }
}
