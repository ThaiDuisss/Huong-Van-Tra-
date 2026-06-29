package org.hvtra.orderservice.service.impl;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.dto.response.CustomerPurchaseResponse;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCustomerServiceImpl implements OrderCustomerService {

    private final CustomerClient customerClient;

    @Override
    public List<CustomerResponse> getCustomers(String q) {
        log.info("Lấy danh sách khách hàng từ Customer-Service, q={}", q);

        return customerClient.getCustomers(q);
    }

    @Override
    @CircuitBreaker(name = "customerService", fallbackMethod = "updateCustomerPurchaseFallback")
    @Retry(name = "customerService")
    public CustomerPurchaseResponse updateCustomerPurchase(UUID orderId) {
        log.info("Gọi Customer-Service để cập nhật mua hàng/tích điểm cho orderId={}", orderId);

        return customerClient.updateCustomerPurchase(orderId);
    }

    public CustomerPurchaseResponse updateCustomerPurchaseFallback(UUID orderId, Throwable ex) {
        log.error(
                "Customer-Service lỗi khi cập nhật mua hàng/tích điểm cho orderId={}. Order vẫn được thanh toán thành công. Lỗi: {}",
                orderId,
                ex.getMessage()
        );

        return null;
    }
}