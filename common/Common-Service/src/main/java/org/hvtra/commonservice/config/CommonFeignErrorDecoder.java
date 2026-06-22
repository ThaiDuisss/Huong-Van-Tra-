package org.hvtra.commonservice.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.hvtra.commonservice.exception.FeignClientException;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CommonFeignErrorDecoder implements ErrorDecoder {
    private final ObjectMapper objectMapper;

    public CommonFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String body = readResponseBody(response);
        String message = extractMessage(body);

        if (message == null || message.isBlank()) {
            message = defaultMessage(status);
        }

        return new FeignClientException(
                status,
                methodKey,
                message,
                body
        );
    }

    private String readResponseBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream inputStream = response.body().asInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(body);

            if (jsonNode.hasNonNull("message")) {
                return jsonNode.get("message").asText();
            }

            if (jsonNode.hasNonNull("error")) {
                return jsonNode.get("error").asText();
            }

            if (jsonNode.hasNonNull("detail")) {
                return jsonNode.get("detail").asText();
            }

            return null;
        } catch (Exception e) {
            return body;
        }
    }

    private String defaultMessage(int statusInt) {
        HttpStatus status = HttpStatus.resolve(statusInt);
        if (status == null) {
            return "Remote service error";
        }

        return switch (status) {
            case BAD_REQUEST -> "Bad request from remote service";
            case UNAUTHORIZED -> "Unauthorized when calling remote service";
            case FORBIDDEN -> "Forbidden when calling remote service";
            case NOT_FOUND -> "Resource not found from remote service";
            case CONFLICT -> "Conflict from remote service";
            case INTERNAL_SERVER_ERROR -> "Remote service internal error";
            case SERVICE_UNAVAILABLE -> "Remote service unavailable";
            case GATEWAY_TIMEOUT -> "Remote service timeout";
            default -> "Remote service error: " + status.getReasonPhrase();
        };
    }
}
