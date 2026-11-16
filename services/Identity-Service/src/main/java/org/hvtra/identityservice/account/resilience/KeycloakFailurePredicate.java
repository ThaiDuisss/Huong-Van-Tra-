package org.hvtra.identityservice.account.resilience;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakUnavailableException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Lỗi nào được circuit breaker {@code keycloak} ghi nhận là Keycloak không khỏe: không kết nối được /
 * timeout (status 0) và HTTP 5xx. HTTP 4xx (400/401/403/404/409...) là câu trả lời nghiệp vụ hoặc lỗi
 * cấu hình quyền, "chưa cấu hình" (-1) là lỗi cục bộ; breaker OPEN / bulkhead đầy không phải bằng chứng
 * Keycloak lỗi. Những trường hợp đó không được làm mở breaker.
 */
public class KeycloakFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof CallNotPermittedException
                    || current instanceof BulkheadFullException
                    || current instanceof KeycloakUnavailableException) {
                return false;
            }

            if (current instanceof KeycloakAdminException keycloakException) {
                int status = keycloakException.getStatus();
                return status == KeycloakAdminException.STATUS_UNREACHABLE || status >= 500;
            }

            if (current instanceof RestClientResponseException responseException) {
                return responseException.getStatusCode().is5xxServerError();
            }

            if (current instanceof ResourceAccessException
                    || current instanceof IOException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
