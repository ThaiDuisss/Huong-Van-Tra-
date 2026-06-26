package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderCodeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "customer-service",
        url = "http://localhost:8082"
)
public interface CustomerClient {

    @GetMapping("/api/customer")
    List<CustomerResponse> getCustomers(@RequestParam(required = false) String q);

    @GetMapping("/api/customer/order-code")
    OrderCodeResponse getOrderCodeResponse(@RequestParam("id") String id);
}