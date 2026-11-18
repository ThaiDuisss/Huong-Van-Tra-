package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderRefundedEvent;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.CustomerPurchaseService;
import org.hvtra.customer.service.LoyaltyService;
import org.hvtra.customer.service.TierQualificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPurchaseServiceImpl implements CustomerPurchaseService {

    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;
    private final TierQualificationService tierQualificationService;

    @Override
    @Transactional
    public boolean onOrderCompleted(OrderCompletedEvent event) {
        if (event.getOrderId() == null || event.getCustomerId() == null) {
            log.debug("Bỏ qua ORDER_COMPLETED không có khách. orderId={}", event.getOrderId());
            return false;
        }
        if (event.getFinalAmount() == null || event.getFinalAmount().signum() <= 0) {
            log.warn("Bỏ qua ORDER_COMPLETED có tổng tiền không hợp lệ. orderId={}, finalAmount={}",
                    event.getOrderId(), event.getFinalAmount());
            return false;
        }
        Optional<Customer> found = lockCustomer(event.getCustomerId(), event.getOrderId());
        if (found.isEmpty()) {
            return false;
        }
        Customer customer = found.get();

        // Tích điểm theo hạng trước đơn này; đơn giúp khách lên hạng thì từ đơn sau mới hưởng hệ số mới.
        loyaltyService.earnForOrder(customer, event.getOrderId(), event.getOrderCode(), event.getFinalAmount());
        customer.addPurchase(event.getFinalAmount(), toLocalDateTime(firstNonNull(event.getCompletedAt(), event.getOccurredAt())));
        boolean upgraded = tierQualificationService.upgradeIfQualified(customer);
        customerRepository.save(customer);

        log.info("Ghi nhận đơn {} cho khách {}: totalSpent={}, totalOrderCount={}, upgraded={}",
                event.getOrderCode(), customer.getCustomerCode(), customer.getTotalSpent(),
                customer.getTotalOrderCount(), upgraded);
        return true;
    }

    @Override
    @Transactional
    public boolean onOrderRefunded(OrderRefundedEvent event) {
        if (event.getOrderId() == null || event.getCustomerId() == null || event.getRefundId() == null) {
            log.debug("Bỏ qua ORDER_REFUNDED thiếu orderId / customerId / refundId. orderId={}", event.getOrderId());
            return false;
        }
        BigDecimal refundAmount = event.getRefundAmount();
        if (refundAmount == null || refundAmount.signum() <= 0) {
            log.warn("Bỏ qua ORDER_REFUNDED có số tiền hoàn không hợp lệ. refundId={}, refundAmount={}",
                    event.getRefundId(), refundAmount);
            return false;
        }
        Optional<Customer> found = lockCustomer(event.getCustomerId(), event.getOrderId());
        if (found.isEmpty()) {
            return false;
        }
        Customer customer = found.get();

        loyaltyService.reverseForRefund(
                customer,
                event.getOrderId(),
                event.getRefundId(),
                event.getRefundCode(),
                refundAmount,
                event.getOrderFinalAmount(),
                event.isFullRefund()
        );
        // Hạng giữ nguyên: chính sách chỉ nâng, không hạ.
        customer.applyRefund(refundAmount, event.isFullRefund());
        customerRepository.save(customer);

        log.info("Ghi nhận hoàn tiền {} của đơn {} cho khách {}: refundAmount={}, fullRefund={}",
                event.getRefundCode(), event.getOrderCode(), customer.getCustomerCode(),
                refundAmount, event.isFullRefund());
        return true;
    }

    private Optional<Customer> lockCustomer(UUID customerId, UUID orderId) {
        Optional<Customer> customer = customerRepository.findByIdForUpdate(customerId);
        if (customer.isEmpty()) {
            log.warn("Bỏ qua event đơn {} vì không tìm thấy khách {} (đã xóa hoặc không tồn tại)", orderId, customerId);
        }
        return customer;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant == null ? Instant.now() : instant, ZoneId.systemDefault());
    }

    private Instant firstNonNull(Instant first, Instant second) {
        return first != null ? first : second;
    }
}
