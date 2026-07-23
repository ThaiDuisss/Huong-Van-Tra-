package org.hvtra.deliveryservice.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hvtra.deliveryservice.exception.GhnIntegrationException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnResponse<T>(
        Integer code,
        String message,
        @JsonProperty("code_message") String codeMessage,
        T data
) {
    public T requireData(String operation) {
        if (code == null || code != 200 || data == null) {
            throw new GhnIntegrationException(operation + " failed: " + message);
        }
        return data;
    }
}
