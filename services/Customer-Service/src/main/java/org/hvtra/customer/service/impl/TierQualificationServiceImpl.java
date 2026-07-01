package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.exception.NoActiveCustomerTierConfiguredException;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.TierQualificationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TierQualificationServiceImpl implements TierQualificationService {
    private final CustomerTierRepository customerTierRepository;

    @Override
    public CustomerTier resolveQualifiedTier(BigDecimal totalSpent, Integer totalOrderCount) {
        BigDecimal spent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
        int orders = totalOrderCount == null ? 0 : totalOrderCount;
        return customerTierRepository.findHighestQualifiedTier(spent, orders)
                .or(() -> customerTierRepository.findLowestActiveTier())
                .orElseThrow(NoActiveCustomerTierConfiguredException::new);
    }
}
