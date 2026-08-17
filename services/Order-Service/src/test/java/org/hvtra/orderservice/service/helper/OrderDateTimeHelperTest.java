package org.hvtra.orderservice.service.helper;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDateTimeHelperTest {

    private final OrderDateTimeHelper helper = new OrderDateTimeHelper();

    @Test
    void startOfDayUsesSystemDefaultZone() {
        LocalDate date = LocalDate.of(2026, 7, 24);

        assertEquals(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                helper.startOfDay(date)
        );
    }
}
