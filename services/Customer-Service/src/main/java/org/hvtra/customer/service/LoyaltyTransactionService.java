package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;

import java.util.List;
import java.util.UUID;

public interface LoyaltyTransactionService {
    List<LoyaltyTransactionResponse> getAll(UUID loyaltyAccountId);

    LoyaltyTransactionResponse getById(UUID id);

    LoyaltyTransactionResponse create(LoyaltyTransactionRequest request);
}
