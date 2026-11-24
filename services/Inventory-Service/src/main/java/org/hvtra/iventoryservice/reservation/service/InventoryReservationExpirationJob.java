package org.hvtra.iventoryservice.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "inventory.reservation.expiration-job", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationExpirationJob {

    private final InventoryReservationService reservationService;

    @Scheduled(fixedDelay = 60_000)
    public void expireReservations() {
        try {
            reservationService.expireReservations();
        } catch (RuntimeException ex) {
            log.error("Không thể expire inventory reservations; sẽ thử lại ở lượt sau", ex);
        }
    }
}
