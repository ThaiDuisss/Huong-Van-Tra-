package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerTierStatus;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.TierQualificationService;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Service
@RequiredArgsConstructor
public class TierQualificationServiceImpl implements TierQualificationService {

    private final CustomerTierRepository customerTierRepository;

    @Override
    public boolean upgradeIfQualified(Customer customer) {
        Optional<CustomerTier> qualified = customerTierRepository.findHighestQualifiedTier(
                zeroIfNull(customer.getTotalSpent()),
                zeroIfNull(customer.getTotalOrderCount())
        );
        if (qualified.isEmpty() || !qualified.get().isHigherThan(customer.getTier())) {
            return false;
        }
        customer.setTier(qualified.get());
        return true;
    }

    @Override
    public Optional<CustomerTier> findNextTier(Customer customer) {
        CustomerTier current = customer.getTier();
        return customerTierRepository
                .findAllByStatusAndIsDeletedFalseOrderByMinSpendingAscMinOrderCountAsc(CustomerTierStatus.ACTIVE)
                .stream()
                .filter(tier -> tier.isHigherThan(current))
                .findFirst();
    }
}
