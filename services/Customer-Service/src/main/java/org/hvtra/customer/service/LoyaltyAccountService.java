package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;

import java.util.UUID;

public interface LoyaltyAccountService {
    LoyaltyAccountResponse getByCustomerId(UUID customerId);

    LoyaltyAccountResponse create(LoyaltyAccountRequest request);

    LoyaltyAccountResponse updateBalance(LoyaltyTransactionRequest request);
}
