package org.hvtra.deliveryservice.model.response;

import org.hvtra.deliveryservice.exception.GhnIntegrationException;
import org.hvtra.deliveryservice.model.response.ghn.GhnResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GhnResponseTest {

    @Test
    void returnsDataForSuccessfulResponse() {
        GhnResponse<String> response = new GhnResponse<>(200, "Success", null, "data");

        assertThat(response.requireData("operation")).isEqualTo("data");
    }

    @Test
    void rejectsFailedOrEmptyResponse() {
        GhnResponse<String> response = new GhnResponse<>(400, "Invalid request", "USER_ERR_COMMON", null);

        assertThatThrownBy(() -> response.requireData("Create order"))
                .isInstanceOf(GhnIntegrationException.class)
                .hasMessageContaining("Invalid request");
    }
}
