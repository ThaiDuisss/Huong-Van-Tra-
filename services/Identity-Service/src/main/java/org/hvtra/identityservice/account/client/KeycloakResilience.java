package org.hvtra.identityservice.account.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Bọc mọi lời gọi HTTP tới Keycloak bằng instance Resilience4j {@code keycloak} (cấu hình trong
 * application.yml). Dùng decorator lập trình thay cho annotation vì {@link KeycloakAdminClient} gọi
 * qua một method private dùng chung (annotation trên self-invocation sẽ bị Spring AOP bỏ qua); thứ tự
 * bọc ở đây là tường minh:
 *
 * <ul>
 *   <li>đọc (GET, idempotent): Retry( CircuitBreaker( Bulkhead( call ) ) )</li>
 *   <li>ghi (POST/PUT/DELETE): CircuitBreaker( Bulkhead( call ) ) — không bao giờ retry</li>
 * </ul>
 *
 * Không có fallback trả dữ liệu giả: breaker OPEN / bulkhead đầy ném
 * {@link KeycloakUnavailableException} (được dịch thành HTTP 503).
 */
@Slf4j
@Component
public class KeycloakResilience {

    public static final String KEYCLOAK = "keycloak";

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    @Autowired
    public KeycloakResilience(RetryRegistry retryRegistry,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              BulkheadRegistry bulkheadRegistry) {
        this(retryRegistry.retry(KEYCLOAK),
                circuitBreakerRegistry.circuitBreaker(KEYCLOAK),
                bulkheadRegistry.bulkhead(KEYCLOAK));
    }

    private KeycloakResilience(Retry retry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    /** Không bọc gì: chỉ dành cho unit test của KeycloakAdminClient không quan tâm tới resilience. */
    static KeycloakResilience disabled() {
        return new KeycloakResilience((Retry) null, (CircuitBreaker) null, (Bulkhead) null);
    }

    /** Lời gọi đọc idempotent: được thử lại khi lỗi tạm thời (KeycloakTransientFailurePredicate). */
    public <T> T read(Supplier<T> call) {
        if (retry == null) {
            return call.get();
        }
        return failFast(Retry.decorateSupplier(retry, guarded(call)));
    }

    /** Lời gọi ghi: không retry, vì request có thể đã được Keycloak xử lý dù phía này thấy lỗi/timeout. */
    public <T> T write(Supplier<T> call) {
        if (circuitBreaker == null) {
            return call.get();
        }
        return failFast(guarded(call));
    }

    private <T> Supplier<T> guarded(Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, Bulkhead.decorateSupplier(bulkhead, call));
    }

    private <T> T failFast(Supplier<T> call) {
        try {
            return call.get();
        } catch (CallNotPermittedException ex) {
            log.warn("Keycloak circuit breaker is OPEN, rejecting call");
            throw new KeycloakUnavailableException("Keycloak đang tạm ngắt do lỗi liên tục, vui lòng thử lại sau", ex);
        } catch (BulkheadFullException ex) {
            log.warn("Keycloak bulkhead is full, rejecting call");
            throw new KeycloakUnavailableException("Đang có quá nhiều yêu cầu tới Keycloak, vui lòng thử lại sau", ex);
        }
    }
}
