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
 * Quyết định lời gọi ĐỌC (GET) tới Keycloak có được thử lại hay không: chỉ lỗi hạ tầng tạm thời
 * (không kết nối được / timeout, HTTP 502/503/504). Không retry 4xx (kể cả 429 vì không có backoff
 * theo Retry-After), HTTP 500, "chưa cấu hình", circuit breaker OPEN hay bulkhead đầy.
 * Lời gọi ghi không bao giờ đi qua Retry nên predicate này không áp dụng cho chúng.
 */
public class KeycloakTransientFailurePredicate implements Predicate<Throwable> {

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
                return status == KeycloakAdminException.STATUS_UNREACHABLE || isTransientStatus(status);
            }

            if (current instanceof RestClientResponseException responseException) {
                return isTransientStatus(responseException.getStatusCode().value());
            }

            if (current instanceof ResourceAccessException
                    || current instanceof IOException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isTransientStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }
}
