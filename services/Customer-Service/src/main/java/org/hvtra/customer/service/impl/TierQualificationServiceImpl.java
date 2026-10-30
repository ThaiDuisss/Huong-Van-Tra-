package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.TierQualificationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static org.hvtra.customer.exception.CustomerServiceException.conflict;

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
                .orElseThrow(() -> conflict(Message.NO_ACTIVE_CUSTOMER_TIER));
    }
}
