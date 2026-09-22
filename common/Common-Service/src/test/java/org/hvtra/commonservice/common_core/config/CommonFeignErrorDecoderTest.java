package org.hvtra.commonservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.hvtra.commonservice.exception.FeignClientException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonFeignErrorDecoderTest {

    private final CommonFeignErrorDecoder decoder = new CommonFeignErrorDecoder(new ObjectMapper());

    @Test
    void decodeExtractsMessageFieldFromJsonBody() {
        Exception exception = decoder.decode(
                "CustomerClient.findById",
                response(404, "{\"message\":\"Customer not found\"}")
        );

        assertThat(exception).isInstanceOf(FeignClientException.class);

        FeignClientException feignException = (FeignClientException) exception;
        assertThat(feignException.getStatus()).isEqualTo(404);
        assertThat(feignException.getMethodKey()).isEqualTo("CustomerClient.findById");
        assertThat(feignException.getMessage()).isEqualTo("Customer not found");
        assertThat(feignException.getResponseBody()).isEqualTo("{\"message\":\"Customer not found\"}");
    }

    @Test
    void decodeExtractsErrorFieldWhenMessageIsMissing() {
        Exception exception = decoder.decode(
                "OrderClient.getOrder",
                response(409, "{\"error\":\"Order already paid\"}")
        );

        assertThat(exception)
                .isInstanceOf(FeignClientException.class)
                .hasMessage("Order already paid");
    }

    @Test
    void decodeExtractsDetailFieldWhenMessageAndErrorAreMissing() {
        Exception exception = decoder.decode(
                "PaymentClient.pay",
                response(400, "{\"detail\":\"Invalid payment amount\"}")
        );

        assertThat(exception)
                .isInstanceOf(FeignClientException.class)
                .hasMessage("Invalid payment amount");
    }

    @Test
    void decodeUsesPlainTextBodyAsMessageWhenBodyIsNotJson() {
        Exception exception = decoder.decode(
                "CatalogClient.getVariant",
                response(404, "Variant not found")
        );

        assertThat(exception)
                .isInstanceOf(FeignClientException.class)
                .hasMessage("Variant not found");
    }

    @Test
    void decodeUsesDefaultMessageWhenBodyIsEmpty() {
        Exception exception = decoder.decode(
                "InventoryClient.reserve",
                response(503, "")
        );

        assertThat(exception)
                .isInstanceOf(FeignClientException.class)
                .hasMessage("Remote service unavailable");
    }

    private Response response(int status, String body) {
        return Response.builder()
                .status(status)
                .reason("Error")
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://remote-service/test",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
