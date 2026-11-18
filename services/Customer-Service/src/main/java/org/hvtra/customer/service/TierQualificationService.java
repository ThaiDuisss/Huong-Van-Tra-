package org.hvtra.customer.service;

import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;

import java.util.Optional;

public interface TierQualificationService {

    /**
     * Nâng hạng nếu tổng chi tiêu và số đơn tích lũy đạt một hạng cao hơn hạng hiện tại.
     * Không bao giờ hạ hạng.
     *
     * @return true nếu hạng của khách đã thay đổi
     */
    boolean upgradeIfQualified(Customer customer);

    /** Hạng đang áp dụng thấp nhất cao hơn hạng hiện tại của khách, nếu có. */
    Optional<CustomerTier> findNextTier(Customer customer);
}
