package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.CustomerResponse;

import java.util.UUID;

public interface OrderCustomerService {

    CustomerResponse getCustomerById(UUID customerId);
}
