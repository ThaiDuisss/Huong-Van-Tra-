package org.hvtra.commonservice.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.domain.constants.HeaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Filter chạy đầu tiên trong mọi service:
 * <ul>
 *   <li>Đưa requestId (header X-Request-ID do gateway gắn), userId và tên service vào MDC
 *       để mọi dòng log của request đều có chung định danh.</li>
 *   <li>Trả lại X-Request-ID trong response để frontend hiển thị khi báo lỗi.</li>
 *   <li>Ghi một dòng access log (logger {@code ACCESS_LOG}) khi request kết thúc:
 *       method, path, status, thời gian xử lý, body đã che thông tin nhạy cảm.</li>
 * </ul>
 * Chạy trước Spring Security nên log có cả các request bị 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final LoggingProperties properties;

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.SERVICE, serviceName);
        String userId = request.getHeader(HeaderConstants.HEADER_USER_ID);
        if (StringUtils.hasText(userId)) {
            MDC.put(MdcKeys.USER_ID, userId);
        }
        response.setHeader(HeaderConstants.HEADER_CORRELATION_ID, requestId);
        boolean writeAccessLog = properties.getAccessLog().isEnabled() && !isSkipped(request);
        if (!writeAccessLog) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.clear();
            }
            return;
        }
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            try {
                writeAccessLog(wrappedRequest, wrappedResponse, durationMs);
            } catch (Exception ex) {
                ACCESS_LOG.warn("Cannot write access log: {}", ex.getMessage());
            }
            // Bắt buộc: đẩy body đã cache ra response thật, nếu không client nhận body rỗng
            wrappedResponse.copyBodyToResponse();
            MDC.clear();
        }

    }
    private void writeAccessLog(ContentCachingRequestWrapper request,
                                ContentCachingResponseWrapper response,
                                long durationMs) {
        String query = request.getQueryString();
        String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        String body = extractBody(request);
        if (body == null) {
            ACCESS_LOG.info("{} {} status={} durationMs={} ip={}",
                    request.getMethod(), path, response.getStatus(), durationMs, clientIp(request));
        } else {
            ACCESS_LOG.info("{} {} status={} durationMs={} ip={} body={}",
                    request.getMethod(), path, response.getStatus(), durationMs, clientIp(request), body);
        }
    }

    private String extractBody(ContentCachingRequestWrapper request) {
        if (!properties.getAccessLog().isLogBody() || !BODY_METHODS.contains(request.getMethod())) {
            return null;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return null;
        }
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String raw = new String(content, StandardCharsets.UTF_8).replaceAll("\\s*\\n\\s*", " ");
        return LogMaskUtils.maskAndTruncate(raw, properties.getAccessLog().getMaxBodyLength());
    }

    private boolean isSkipped(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return properties.getAccessLog().getSkipPaths().stream().anyMatch(uri::startsWith);
    }


    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(HeaderConstants.HEADER_CORRELATION_ID);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        // Gọi trực tiếp không qua gateway (dev, test) thì tự sinh
        return UUID.randomUUID().toString();
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
