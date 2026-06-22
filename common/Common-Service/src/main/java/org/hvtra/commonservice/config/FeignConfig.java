package org.hvtra.commonservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.hvtra.commonservice.domain.constants.HeaderConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper);
    }
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            HeaderConstants.FORWARD_HEADERS.forEach(headerName -> {
                String headerValue = request.getHeader(headerName);

                if (StringUtils.hasText(headerValue)) {
                    requestTemplate.header(headerName, headerValue);
                }
            });
        };
    }
    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
