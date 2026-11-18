package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.config.LoyaltyProperties;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyAdjustmentRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.LoyaltyService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static org.hvtra.customer.exception.CustomerServiceException.invalidRequest;
import static org.hvtra.customer.exception.CustomerServiceException.notFound;
import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyServiceImpl implements LoyaltyService {

    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final CustomerMapper customerMapper;
    private final LoyaltyProperties loyaltyProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoyaltyTransactionResponse> getTransactions(UUID customerId, Pageable pageable) {
        if (customerRepository.findByIdAndIsDeletedFalse(customerId).isEmpty()) {
            throw notFound(Message.CUSTOMER_NOT_FOUND);
        }
        return PageResponse.from(
                loyaltyTransactionRepository.findByCustomer_IdAndIsDeletedFalse(customerId, pageable)
                        .map(customerMapper::toResponse)
        );
    }

    @Override
    @Transactional
    public LoyaltyTransactionResponse adjust(UUID customerId, LoyaltyAdjustmentRequest request) {
        int points = request.points() == null ? 0 : request.points();
        if (points == 0) {
            throw invalidRequest(Message.LOYALTY_POINTS_REQUIRED);
        }
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
        if (zeroIfNull(customer.getPointBalance()) + points < 0) {
            throw invalidRequest(Message.LOYALTY_POINTS_INSUFFICIENT.formatted(zeroIfNull(customer.getPointBalance())));
        }

        LoyaltyTransaction transaction = record(
                customer,
                LoyaltyTransactionType.ADJUST,
                points,
                LoyaltyReferenceType.MANUAL,
                null,
                null,
                null,
                request.reason().trim()
        );
        customerRepository.save(customer);
        return customerMapper.toResponse(transaction);
    }

    @Override
    public void earnForOrder(Customer customer, UUID orderId, String orderCode, BigDecimal paidAmount) {
        if (loyaltyTransactionRepository.existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.EARN, LoyaltyReferenceType.ORDER, orderId)) {
            return;
        }
        int points = pointsFor(paidAmount, customer.getTier());
        if (points <= 0) {
            return;
        }
        record(
                customer,
                LoyaltyTransactionType.EARN,
                points,
                LoyaltyReferenceType.ORDER,
                orderId,
                orderCode,
                orderId,
                null
        );
    }

    @Override
    public void reverseForRefund(
            Customer customer,
            UUID orderId,
            UUID refundId,
            String refundCode,
            BigDecimal refundAmount,
            BigDecimal orderAmount,
            boolean fullRefund
    ) {
        if (loyaltyTransactionRepository.existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.REVERSE, LoyaltyReferenceType.REFUND, refundId)) {
            return;
        }
        int earned = loyaltyTransactionRepository
                .findByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                        LoyaltyTransactionType.EARN, LoyaltyReferenceType.ORDER, orderId)
                .map(LoyaltyTransaction::getPoints)
                .orElse(0);
        if (earned <= 0) {
            return;
        }
        // REVERSE lưu số âm, nên tổng đã trừ là giá trị tuyệt đối.
        int alreadyReversed = (int) Math.abs(
                loyaltyTransactionRepository.sumPointsByOrderAndType(orderId, LoyaltyTransactionType.REVERSE)
        );
        int remaining = earned - alreadyReversed;
        if (remaining <= 0) {
            return;
        }

        int toReverse = fullRefund ? remaining : Math.min(proportionalPoints(earned, refundAmount, orderAmount), remaining);
        // Khách đã dùng / bị trừ bớt điểm thì chỉ trừ đến 0.
        toReverse = Math.min(toReverse, zeroIfNull(customer.getPointBalance()));
        if (toReverse <= 0) {
            return;
        }
        record(
                customer,
                LoyaltyTransactionType.REVERSE,
                -toReverse,
                LoyaltyReferenceType.REFUND,
                refundId,
                refundCode,
                orderId,
                fullRefund ? "Hoàn tiền toàn bộ đơn" : "Hoàn tiền một phần đơn"
        );
    }

    //======================================= Helpers =======================================

    /** floor(paidAmount / spendPerPoint) × hệ số của hạng, làm tròn xuống. */
    private int pointsFor(BigDecimal paidAmount, CustomerTier tier) {
        BigDecimal spendPerPoint = loyaltyProperties.getPoints().getSpendPerPoint();
        if (paidAmount == null || paidAmount.signum() <= 0 || spendPerPoint == null || spendPerPoint.signum() <= 0) {
            return 0;
        }
        BigDecimal multiplier = tier == null || tier.getPointMultiplier() == null
                ? BigDecimal.ONE
                : tier.getPointMultiplier();
        return paidAmount.divide(spendPerPoint, 0, RoundingMode.DOWN)
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .intValueExact();
    }

    /** floor(earned × refundAmount / orderAmount). */
    private int proportionalPoints(int earned, BigDecimal refundAmount, BigDecimal orderAmount) {
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return 0;
        }
        if (orderAmount == null || orderAmount.signum() <= 0 || refundAmount.compareTo(orderAmount) >= 0) {
            return earned;
        }
        return BigDecimal.valueOf(earned)
                .multiply(refundAmount)
                .divide(orderAmount, 0, RoundingMode.DOWN)
                .intValueExact();
    }

    private LoyaltyTransaction record(
            Customer customer,
            LoyaltyTransactionType type,
            int points,
            LoyaltyReferenceType referenceType,
            UUID referenceId,
            String referenceCode,
            UUID orderId,
            String reason
    ) {
        int balanceAfter = customer.changePoints(points);

        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomer(customer);
        transaction.setType(type);
        transaction.setPoints(points);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceCode(referenceCode);
        transaction.setOrderId(orderId);
        transaction.setReason(reason);

        log.info("Loyalty {} {} điểm cho customerId={}, số dư mới={}",
                type, points, customer.getId(), balanceAfter);
        return loyaltyTransactionRepository.save(transaction);
    }
}
