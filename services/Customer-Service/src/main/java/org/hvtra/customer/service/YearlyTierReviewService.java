package org.hvtra.customer.service;

import java.time.LocalDateTime;
import java.util.UUID;

public interface YearlyTierReviewService {
    void reviewCurrentYear();

    void reviewCustomerTier(UUID customerId, int currentYear, LocalDateTime reviewTime);
}
