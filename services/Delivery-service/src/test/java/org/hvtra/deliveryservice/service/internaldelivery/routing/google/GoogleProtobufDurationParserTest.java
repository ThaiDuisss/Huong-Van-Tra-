package org.hvtra.deliveryservice.service.internaldelivery.routing.google;

import org.hvtra.deliveryservice.service.internalDelivery.routing.google.GoogleProtobufDurationParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
class GoogleProtobufDurationParserTest {

    @Test
    void roundsFractionalSecondUpForSafeEta() {
        assertThat(GoogleProtobufDurationParser.parseSecond("612.4s")).isEqualTo(613);
    }

    @Test
    void rejectsInvalidDuration() {
        assertThatThrownBy(() -> GoogleProtobufDurationParser.parseSecond("612"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
