package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface CustomerService {
    PageResponse<CustomerResponse> findAll(
            CustomerFilterRequest filter,
            Pageable pageable);

    CustomerResponse getById(UUID id);

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(UUID id, CustomerRequest request);

    CustomerResponse updatePurchase(UUID orderId);

    void delete(UUID id);
}
