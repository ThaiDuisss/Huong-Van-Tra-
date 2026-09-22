package org.hvtra.commonservice.common_core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Retryer;
import feign.Target;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.hvtra.commonservice.common_core.logging.MdcKeys;
import org.slf4j.MDC;
import org.hvtra.commonservice.domain.entity.InternalUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Configuration
public class FeignConfig {

    @Value("${security.internal.header-secret:dev-internal-header-secret}")
    private String headerSecret;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper);
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            if (isExternalTarget(requestTemplate)) {
                return;
            }

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

    private void attachInternalServiceAuthentication(RequestTemplate requestTemplate) {
        String userId = "service:" + applicationName;
        String roles = "SYSTEM";
        String correlationId = UUID.randomUUID().toString();
        String authorization = "Bearer service:" + applicationName;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InternalUserPrincipal principal) {
            if (StringUtils.hasText(principal.userId())) {
                userId = principal.userId();
            }
            if (principal.roles() != null && !principal.roles().isEmpty()) {
                roles = String.join(",", principal.roles());
            }
            if (StringUtils.hasText(principal.correlationId())) {
                correlationId = principal.correlationId();
            }
        }

        String signature = sign(userId, roles, correlationId, authorization);

        requestTemplate.header(HeaderConstants.HEADER_USER_ID, userId);
        requestTemplate.header(HeaderConstants.HEADER_USER_ROLES, roles);
        requestTemplate.header(HeaderConstants.HEADER_CORRELATION_ID, correlationId);
        requestTemplate.header(HeaderConstants.HEADER_AUTHOR, authorization);
        requestTemplate.header(HeaderConstants.HEADER_SIGNATURE, signature);
    }

    private boolean isExternalTarget(RequestTemplate template) {
        Target<?> target = template.feignTarget();
        if (target != null) {
            String name = target.name() != null ? target.name().toLowerCase() : "";
            String url = target.url() != null ? target.url().toLowerCase() : "";
            if (name.contains("ghn") || name.contains("ahamove") || name.contains("google")
                    || url.contains("ghn.vn") || url.contains("ahamove.com") || url.contains("googleapis.com")) {
                return true;
            }
        }
        return false;
    }

    private String sign(
            String userId,
            String roles,
            String correlationId,
            String authorization
    ) {
        try {
            String raw = String.join("\n",
                    value(userId),
                    value(roles),
                    value(correlationId),
                    value(authorization));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    headerSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign internal request context", ex);
        }
    }

    private String value(String val) {
        return val == null ? "" : val;
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
