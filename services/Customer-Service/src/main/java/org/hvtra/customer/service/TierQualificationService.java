package org.hvtra.customer.service;

import org.hvtra.customer.model.entity.CustomerTier;

import java.math.BigDecimal;

public interface TierQualificationService {
    CustomerTier resolveQualifiedTier(BigDecimal totalSpent, Integer totalOrderCount);
}
