package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.response.CustomerAddressResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerAddressService {
    List<CustomerAddressResponse> getAll(UUID customerId);

    CustomerAddressResponse getById(UUID id);

    CustomerAddressResponse create(CustomerAddressRequest request);

    CustomerAddressResponse update(UUID id, CustomerAddressRequest request);

    void delete(UUID id);
}
