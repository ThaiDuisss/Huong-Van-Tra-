package org.hvtra.deliveryservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class GhnFeignConfig {
    @Bean
    public RequestInterceptor ghnInterceptor(GhnProperties properties) {
        return requestTemplate ->  {
            String token = properties.token();
            requestTemplate.header(
                    "Token",
                    token
            );
        };
    }
}
