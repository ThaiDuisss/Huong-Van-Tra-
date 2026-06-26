package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCustomerService {

    private final CustomerClient customerClient;

    public List<CustomerResponse> getCustomers(String q) {
        log.info("vao day rôi");
        return customerClient.getCustomers(q);
    }
}