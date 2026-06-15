package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;

import java.util.List;
import java.util.UUID;

public interface LoyaltyAccountService {
    List<LoyaltyAccountResponse> getAll();

    LoyaltyAccountResponse getById(UUID id);

    LoyaltyAccountResponse getByCustomerId(UUID customerId);

    LoyaltyAccountResponse create(LoyaltyAccountRequest request);

    LoyaltyAccountResponse update(UUID id, LoyaltyAccountRequest request);

    void delete(UUID id);
}
