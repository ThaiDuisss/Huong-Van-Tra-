package org.hvtra.customer.service;

import org.hvtra.customer.model.entity.LoyaltyPointRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyPointRuleService {
    List<LoyaltyPointRule> getAll();

    LoyaltyPointRule getById(UUID id);

    Optional<LoyaltyPointRule> findApplicableRule(UUID tierId, BigDecimal orderAmount, Instant effectiveAt);

    LoyaltyPointRule create(LoyaltyPointRule rule);

    LoyaltyPointRule update(UUID id, LoyaltyPointRule rule);

    void delete(UUID id);
}
