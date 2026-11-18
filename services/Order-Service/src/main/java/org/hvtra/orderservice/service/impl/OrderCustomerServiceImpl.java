package org.hvtra.orderservice.service.impl;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCustomerServiceImpl implements OrderCustomerService {

    private final CustomerClient customerClient;

    @Override
    @CircuitBreaker(name = "customerService", fallbackMethod = "getCustomerByIdFallback")
    @Retry(name = "customerService")
    public CustomerResponse getCustomerById(UUID customerId) {
        log.info("Lấy snapshot khách hàng từ Customer-Service, customerId={}", customerId);

        return customerClient.getCustomerById(customerId);
    }

    public CustomerResponse getCustomerByIdFallback(UUID customerId, Throwable ex) {
        log.error(
                "Customer-Service lỗi khi lấy snapshot khách hàng customerId={}. Đơn hàng vẫn được tạo nếu customer không bắt buộc. Lỗi: {}",
                customerId,
                ex.getMessage()
        );

        return null;
    }
}
