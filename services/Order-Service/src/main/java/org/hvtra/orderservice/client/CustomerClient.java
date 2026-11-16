package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.CustomerPurchaseResponse;
import org.hvtra.orderservice.client.resilience.IdempotentRead;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "customer-service",
        url = "${clients.customer-service.url:http://localhost:8082}"
)
public interface CustomerClient {

    @IdempotentRead
    @GetMapping("/api/customer")
    List<CustomerResponse> getCustomers(@RequestParam(required = false) String q);

    @IdempotentRead
    @GetMapping("/api/customer/{id}")
    CustomerResponse getCustomerById(@PathVariable("id") UUID id);

    @PutMapping("/api/customer/purchases/{orderId}")
    CustomerPurchaseResponse updateCustomerPurchase(@PathVariable("orderId") UUID orderId);
}
