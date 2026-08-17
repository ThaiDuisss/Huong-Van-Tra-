package org.hvtra.orderservice.service.helper;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class OrderDateTimeHelper {

    private static final long DRAFT_TTL_MINUTES = 15;

    public Instant nextDraftExpiresAt() {
        return Instant.now().plusSeconds(DRAFT_TTL_MINUTES * 60);
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    public LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }
}
