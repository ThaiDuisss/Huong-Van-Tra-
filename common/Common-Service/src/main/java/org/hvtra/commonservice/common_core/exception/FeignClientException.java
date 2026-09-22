package org.hvtra.commonservice.common_core.exception;

public class FeignClientException extends RuntimeException {

    private final int status;
    private final String methodKey;
    private final String responseBody;

    public FeignClientException(
            int status,
            String methodKey,
            String message,
            String responseBody
    ) {
        super(message);
        this.status = status;
        this.methodKey = methodKey;
        this.responseBody = responseBody;
    }

    public int getStatus() {
        return status;
    }

    public String getMethodKey() {
        return methodKey;
    }

    public String getResponseBody() {
        return responseBody;
    }
}