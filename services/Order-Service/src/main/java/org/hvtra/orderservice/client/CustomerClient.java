package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.response.CustomerLookupResponse;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Chi tiêu, số đơn, hạng và điểm của khách do Customer-Service tự cập nhật từ event outbox
 * (order.completed, customer.order-refunded), nên client này chỉ còn các API đọc.
 */
@FeignClient(
        name = "customer-service",
        url = "${clients.customer-service.url:http://localhost:8082}"
)
public interface CustomerClient {

    /** Tra cứu nhanh khách ACTIVE theo tên / SĐT / mã; chỉ trả id, name, code, phone. */
    @GetMapping("/api/customer/lookup")
    List<CustomerLookupResponse> lookupCustomers(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "size", required = false) Integer size
    );

    @GetMapping("/api/customer/{id}")
    CustomerResponse getCustomerById(@PathVariable("id") UUID id);
}
