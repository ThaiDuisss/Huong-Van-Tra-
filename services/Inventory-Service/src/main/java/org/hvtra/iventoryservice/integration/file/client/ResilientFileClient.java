package org.hvtra.iventoryservice.integration.file.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Resilience4j decorator around Common's File-Service Feign proxy; {@code @Primary}, so it is what
 * every {@code FileClient} injection point receives, including Common's ImportBatchServiceImpl
 * (import drafts attach their source file). Nesting: CircuitBreaker( Bulkhead( call ) ).
 *
 * <p>{@code attachFile} is a write made inside the import-draft transaction, so it is never
 * retried. The caller already catches and logs a failed attach; with the breaker open it fails
 * immediately instead of holding the transaction for the read timeout.
 */
@Primary
@Component
@CircuitBreaker(name = ResilientFileClient.INSTANCE)
@Bulkhead(name = ResilientFileClient.INSTANCE)
public class ResilientFileClient implements FileClient {

    public static final String INSTANCE = "fileService";

    /** Bean alias Spring Cloud OpenFeign gives the raw proxy: {@code <client name>FeignClient}. */
    public static final String FEIGN_QUALIFIER = "file-serviceFeignClient";

    private final FileClient delegate;

    public ResilientFileClient(@Qualifier(FEIGN_QUALIFIER) FileClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public void attachFile(FileAttach fileAttach) {
        delegate.attachFile(fileAttach);
    }
}
