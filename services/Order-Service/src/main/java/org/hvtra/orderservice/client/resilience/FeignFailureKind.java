package org.hvtra.orderservice.client.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** What a failed Feign call says about the downstream service. */
public enum FeignFailureKind {

    /** Refused locally by a circuit breaker or bulkhead; no request was sent. */
    LOCAL_REJECTION,
    /** Could not connect, or the connection broke or timed out. */
    NETWORK,
    /** HTTP 502, 503 or 504: the service or the hop in front of it is temporarily unavailable. */
    GATEWAY_UNAVAILABLE,
    /** Any other HTTP 5xx: the service is unhealthy, but repeating the request will not help. */
    SERVER_ERROR,
    /** HTTP 4xx and anything that is not a transport or server failure: the service answered. */
    NOT_A_SERVICE_FAILURE;

    /** Walks the cause chain and classifies the first exception that carries a verdict. */
    public static FeignFailureKind of(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CallNotPermittedException
                    || current instanceof BulkheadFullException
                    || current instanceof DownstreamUnavailableException) {
                return LOCAL_REJECTION;
            }
            if (current instanceof RetryableException
                    || current instanceof SocketException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException) {
                return NETWORK;
            }
            if (current instanceof FeignException feignException) {
                return ofStatus(feignException.status());
            }
            if (current instanceof FeignClientException feignClientException) {
                return ofStatus(feignClientException.getStatus());
            }
            current = current.getCause();
        }
        return NOT_A_SERVICE_FAILURE;
    }

    private static FeignFailureKind ofStatus(int status) {
        if (status == 502 || status == 503 || status == 504) {
            return GATEWAY_UNAVAILABLE;
        }
        return status >= 500 ? SERVER_ERROR : NOT_A_SERVICE_FAILURE;
    }
}
