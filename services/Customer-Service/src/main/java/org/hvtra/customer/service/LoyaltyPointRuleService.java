package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.LoyaltyPointRuleRequest;
import org.hvtra.customer.dto.response.LoyaltyPointRuleResponse;
import org.hvtra.customer.model.entity.LoyaltyPointRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyPointRuleService {
    List<LoyaltyPointRuleResponse> getAll();

    LoyaltyPointRuleResponse getById(UUID id);

    Optional<LoyaltyPointRule> findApplicableRule(UUID tierId, BigDecimal orderAmount, Instant effectiveAt);

    LoyaltyPointRuleResponse create(LoyaltyPointRuleRequest request);

    LoyaltyPointRuleResponse update(UUID id, LoyaltyPointRuleRequest request);

    void delete(UUID id);
}
