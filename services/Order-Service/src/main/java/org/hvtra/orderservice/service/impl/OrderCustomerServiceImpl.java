package org.hvtra.orderservice.service.impl;

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

    // Circuit breaker, bulkhead và retry của customerService nằm ngay trong Feign client
    // (client.resilience); ở đây chỉ còn phần giảm cấp: lỗi Customer-Service không chặn đơn hàng.
    @Override
    public CustomerResponse getCustomerById(UUID customerId) {
        log.info("Lấy snapshot khách hàng từ Customer-Service, customerId={}", customerId);

        try {
            return customerClient.getCustomerById(customerId);
        } catch (RuntimeException ex) {
            log.error(
                    "Customer-Service lỗi khi lấy snapshot khách hàng customerId={}. Đơn hàng vẫn được tạo nếu customer không bắt buộc. Lỗi: {}",
                    customerId,
                    ex.getMessage()
            );
            return null;
        }
    }

    @Override
    public CustomerPurchaseResponse updateCustomerPurchase(UUID orderId) {
        log.info("Gọi Customer-Service để cập nhật mua hàng/tích điểm cho orderId={}", orderId);

        try {
            return customerClient.updateCustomerPurchase(orderId);
        } catch (RuntimeException ex) {
            log.error(
                    "Customer-Service lỗi khi cập nhật mua hàng/tích điểm cho orderId={}. Order vẫn được thanh toán thành công. Lỗi: {}",
                    orderId,
                    ex.getMessage()
            );
            return null;
        }
    }
}
