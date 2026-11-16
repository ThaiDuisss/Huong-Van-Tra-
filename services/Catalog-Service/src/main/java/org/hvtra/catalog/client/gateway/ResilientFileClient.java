package org.hvtra.catalog.client.gateway;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.exception.FileServiceUnavailableException;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Resilience4j-protected decorator of the shared {@link FileClient}.
 *
 * <p>It is the {@code @Primary} {@link FileClient} of Catalog, so every injection point receives it
 * without code changes: ProductServiceImpl and also ImportBatchServiceImpl, which lives in Common-Service
 * and cannot depend on Resilience4j. The raw Feign proxy is injected here by its Spring Cloud OpenFeign
 * qualifier ({@code <client name>FeignClient}).
 *
 * <p>Aspect order (Resilience4j defaults): CircuitBreaker( Bulkhead( call ) ).
 *
 * <p>{@code attachFile} is a write and is deliberately NOT retried. File-Service's
 * {@code PUT /api/file/attach} only sets {@code refId} on the file row, so repeating it would be
 * harmless, but both callers run inside a database transaction (product creation also holds the
 * PRODUCT sequence row lock): a retry would double the time that lock is held, and the user can simply
 * submit again.
 *
 * <p>Failure contract: nothing is ever reported as success. An open breaker or a full bulkhead becomes
 * {@link FileServiceUnavailableException} (HTTP 503). Every other error (FeignClientException with the
 * downstream status, RetryableException on timeout) is rethrown unchanged so the existing error
 * translation still applies.
 */
@Slf4j
@Primary
@Component
public class ResilientFileClient implements FileClient {

    static final String FILE_SERVICE = "fileService";
    static final String FEIGN_DELEGATE_QUALIFIER = "file-serviceFeignClient";

    private final FileClient delegate;

    public ResilientFileClient(@Qualifier(FEIGN_DELEGATE_QUALIFIER) FileClient delegate) {
        this.delegate = delegate;
    }

    @Override
    @CircuitBreaker(name = FILE_SERVICE, fallbackMethod = "attachFileRejected")
    @Bulkhead(name = FILE_SERVICE)
    public void attachFile(FileAttach fileAttach) {
        delegate.attachFile(fileAttach);
    }

    private void attachFileRejected(FileAttach fileAttach, CallNotPermittedException exception) {
        log.warn("File circuit breaker is OPEN, attach was not sent. fileId={} refId={}",
                fileAttach.fieldId(), fileAttach.refId());
        throw new FileServiceUnavailableException(
                "File Service đang tạm ngắt do lỗi liên tục. Vui lòng thử lại sau.",
                exception
        );
    }

    private void attachFileRejected(FileAttach fileAttach, BulkheadFullException exception) {
        log.warn("File bulkhead is full, attach was not sent. fileId={} refId={}",
                fileAttach.fieldId(), fileAttach.refId());
        throw new FileServiceUnavailableException(
                "Hệ thống đang có quá nhiều yêu cầu gắn tệp. Vui lòng thử lại sau.",
                exception
        );
    }
}
