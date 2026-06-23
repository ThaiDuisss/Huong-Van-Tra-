package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(
        name = "customer-service",
        url = "http://localhost:8082"
)
public interface CustomerClient {

    @GetMapping("/api/customer")
    List<CustomerResponse> getCustomers();
}