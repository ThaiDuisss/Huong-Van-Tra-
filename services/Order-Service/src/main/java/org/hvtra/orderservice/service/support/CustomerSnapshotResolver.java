package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerSnapshotResolver {

    private final OrderCustomerService orderCustomerService;

    public CustomerResponse resolve(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            return orderCustomerService.getCustomerById(customerId);
        } catch (RuntimeException ex) {
            log.warn("Không lấy được snapshot khách hàng customerId={}: {}", customerId, ex.getMessage());
            return null;
        }
    }
}
