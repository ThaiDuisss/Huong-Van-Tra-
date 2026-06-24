package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCustomerService {

    private final CustomerClient customerClient;

    public List<CustomerResponse> getCustomers(String q) {
        return customerClient.getCustomers(q);
    }
}