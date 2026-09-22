package org.hvtra.commonservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.hvtra.commonservice.domain.constants.HeaderConstants;
import org.hvtra.commonservice.logging.MdcKeys;
import org.slf4j.MDC;
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
                // Gọi Feign ngoài luồng HTTP (consumer Kafka, @Async, scheduler):
                // vẫn truyền X-Request-ID lấy từ MDC để nối log giữa các service
                String requestId = MDC.get(MdcKeys.REQUEST_ID);
                if (StringUtils.hasText(requestId)) {
                    requestTemplate.header(HeaderConstants.HEADER_CORRELATION_ID, requestId);
                }
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
