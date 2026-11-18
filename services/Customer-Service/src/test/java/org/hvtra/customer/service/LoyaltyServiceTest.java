package org.hvtra.customer.service;

import org.hvtra.customer.config.LoyaltyProperties;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyAdjustmentRequest;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.impl.LoyaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();

    private CustomerRepository customerRepository;
    private LoyaltyTransactionRepository transactionRepository;
    private LoyaltyServiceImpl loyaltyService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        transactionRepository = mock(LoyaltyTransactionRepository.class);
        LoyaltyProperties properties = new LoyaltyProperties();
        properties.getPoints().setSpendPerPoint(new BigDecimal("10000"));
        loyaltyService = new LoyaltyServiceImpl(customerRepository, transactionRepository, new CustomerMapper(), properties);
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void earnRoundsDownAndAppliesTierMultiplier() {
        Customer customer = customer(0, "1.50");

        loyaltyService.earnForOrder(customer, ORDER_ID, "DH-1", new BigDecimal("129000"));

        // floor(129000 / 10000) = 12; 12 × 1.5 = 18
        LoyaltyTransaction saved = savedTransaction();
        assertThat(saved.getType()).isEqualTo(LoyaltyTransactionType.EARN);
        assertThat(saved.getPoints()).isEqualTo(18);
        assertThat(saved.getBalanceAfter()).isEqualTo(18);
        assertThat(saved.getReferenceCode()).isEqualTo("DH-1");
        assertThat(customer.getPointBalance()).isEqualTo(18);
    }

    @Test
    void earnIsIdempotentPerOrder() {
        Customer customer = customer(0, "1");
        when(transactionRepository.existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.EARN, LoyaltyReferenceType.ORDER, ORDER_ID)).thenReturn(true);

        loyaltyService.earnForOrder(customer, ORDER_ID, "DH-1", new BigDecimal("500000"));

        verify(transactionRepository, never()).save(any());
        assertThat(customer.getPointBalance()).isZero();
    }

    @Test
    void partialRefundReversesProportionallyAndFullRefundReversesTheRest() {
        Customer customer = customer(50, "1");
        stubEarned(20);
        when(transactionRepository.sumPointsByOrderAndType(ORDER_ID, LoyaltyTransactionType.REVERSE)).thenReturn(0L);

        // Hoàn 25% đơn 200.000đ đã tích 20 điểm → trừ 5.
        loyaltyService.reverseForRefund(customer, ORDER_ID, UUID.randomUUID(), "TH-1",
                new BigDecimal("50000"), new BigDecimal("200000"), false);
        assertThat(customer.getPointBalance()).isEqualTo(45);

        // Đã trừ 5, hoàn nốt toàn bộ → trừ 15 còn lại.
        when(transactionRepository.sumPointsByOrderAndType(ORDER_ID, LoyaltyTransactionType.REVERSE)).thenReturn(-5L);
        loyaltyService.reverseForRefund(customer, ORDER_ID, UUID.randomUUID(), "TH-2",
                new BigDecimal("150000"), new BigDecimal("200000"), true);
        assertThat(customer.getPointBalance()).isEqualTo(30);
    }

    @Test
    void refundNeverDropsBalanceBelowZero() {
        Customer customer = customer(3, "1");
        stubEarned(20);
        when(transactionRepository.sumPointsByOrderAndType(ORDER_ID, LoyaltyTransactionType.REVERSE)).thenReturn(0L);

        loyaltyService.reverseForRefund(customer, ORDER_ID, UUID.randomUUID(), "TH-1",
                new BigDecimal("200000"), new BigDecimal("200000"), true);

        assertThat(customer.getPointBalance()).isZero();
        assertThat(savedTransaction().getPoints()).isEqualTo(-3);
    }

    @Test
    void adjustRejectsSubtractingMoreThanBalance() {
        Customer customer = customer(10, "1");
        when(customerRepository.findByIdForUpdate(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> loyaltyService.adjust(customer.getId(), new LoyaltyAdjustmentRequest(-11, "Sửa sai")))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_POINTS_INSUFFICIENT.formatted(10));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void adjustRecordsManualTransactionWithBalance() {
        Customer customer = customer(10, "1");
        when(customerRepository.findByIdForUpdate(customer.getId())).thenReturn(Optional.of(customer));

        var response = loyaltyService.adjust(customer.getId(), new LoyaltyAdjustmentRequest(-4, " Sửa sai "));

        assertThat(response.type()).isEqualTo(LoyaltyTransactionType.ADJUST);
        assertThat(response.points()).isEqualTo(-4);
        assertThat(response.balanceAfter()).isEqualTo(6);
        assertThat(response.reason()).isEqualTo("Sửa sai");
    }

    private void stubEarned(int points) {
        LoyaltyTransaction earn = new LoyaltyTransaction();
        earn.setPoints(points);
        when(transactionRepository.findByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.EARN, LoyaltyReferenceType.ORDER, ORDER_ID)).thenReturn(Optional.of(earn));
    }

    private LoyaltyTransaction savedTransaction() {
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private Customer customer(int pointBalance, String multiplier) {
        CustomerTier tier = new CustomerTier();
        tier.setPointMultiplier(new BigDecimal(multiplier));
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setPointBalance(pointBalance);
        customer.setTier(tier);
        return customer;
    }
}
