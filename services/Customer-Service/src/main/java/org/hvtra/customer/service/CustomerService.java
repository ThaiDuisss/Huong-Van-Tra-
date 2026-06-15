package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerService {
    List<CustomerResponse> getAll();

    CustomerResponse getById(UUID id);

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);
}
