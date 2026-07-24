package org.hvtra.deliveryservice.service.internalDelivery.helper;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
public class InternalRouteCodeGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

    public String generate(Instant createdAt) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "IR-" + FORMATTER.format(createdAt) + "-" + suffix;
    }
}
