package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerTierCountResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerTierService {
    List<CustomerTierResponse> getAll();

    List<CustomerTierCountResponse> getCounts();

    CustomerTierResponse getById(UUID id);

    CustomerTierResponse create(CustomerTierRequest request);

    CustomerTierResponse update(UUID id, CustomerTierRequest request);

    void delete(UUID id);
}
