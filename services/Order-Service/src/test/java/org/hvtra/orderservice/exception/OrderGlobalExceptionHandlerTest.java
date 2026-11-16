package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.orderservice.client.resilience.DownstreamService;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import static org.assertj.core.api.Assertions.assertThat;

class OrderGlobalExceptionHandlerTest {

    private final OrderGlobalExceptionHandler handler = new OrderGlobalExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(OrderGlobalExceptionHandler.class);

    @Test
    void refusedDownstreamCallIsAnswered503WithRetryAfter() {
        DownstreamUnavailableException refused = new DownstreamUnavailableException(
                DownstreamService.PAYMENT, Reason.CIRCUIT_OPEN, "PaymentClient#checkout", null);

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(refused);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("10");
        assertThat(response.getBody().code()).isEqualTo("DOWNSTREAM_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).contains("thanh toán");
    }

    @Test
    void refusedCallUsesItsOwnHandlerWhileOtherDownstreamErrorsKeepTheSharedOne() {
        DownstreamUnavailableException refused = new DownstreamUnavailableException(
                DownstreamService.INVENTORY, Reason.BULKHEAD_FULL, "InventoryClient#fulfillSale", null);
        FeignClientException downstreamError = new FeignClientException(409, "InventoryClient#applyReservation", "x", null);

        assertThat(resolver.resolveMethodByThrowable(refused).getName()).isEqualTo("handleDownstreamUnavailable");
        assertThat(resolver.resolveMethodByThrowable(downstreamError).getName()).isEqualTo("handleFeignClientException");
    }
}
