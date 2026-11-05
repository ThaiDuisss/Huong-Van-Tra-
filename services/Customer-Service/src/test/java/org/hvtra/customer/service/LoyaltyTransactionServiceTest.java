package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.LoyaltyAccountRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.impl.LoyaltyTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoyaltyTransactionServiceTest {

    private LoyaltyTransactionRepository transactionRepository;
    private LoyaltyAccountRepository accountRepository;
    private LoyaltyTransactionServiceImpl transactionService;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(LoyaltyTransactionRepository.class);
        accountRepository = mock(LoyaltyAccountRepository.class);
        transactionService = new LoyaltyTransactionServiceImpl(transactionRepository, accountRepository, new CustomerMapper());
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        account = new LoyaltyAccount();
        account.setId(UUID.randomUUID());
        account.setCustomer(customer);
        account.setPointBalance(100);
        when(accountRepository.findByIdAndIsDeletedFalse(account.getId())).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAddsPositivePointsToBalance() {
        UUID orderId = UUID.randomUUID();

        LoyaltyTransactionResponse response = transactionService.create(new LoyaltyTransactionRequest(
                account.getId(), LoyaltyTransactionType.EARN, 50, LoyaltyReferenceType.ORDER, orderId));

        assertThat(account.getPointBalance()).isEqualTo(150);
        assertThat(response.points()).isEqualTo(50);
        assertThat(response.referenceId()).isEqualTo(orderId);
        assertThat(response.customerId()).isEqualTo(account.getCustomer().getId());
        verify(accountRepository).save(account);
    }

    @Test
    void createSubtractsNegativePointsFromBalance() {
        transactionService.create(new LoyaltyTransactionRequest(
                account.getId(), LoyaltyTransactionType.EARN, -30, null, null));

        assertThat(account.getPointBalance()).isEqualTo(70);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 0)
    void createRejectsMissingOrZeroPoints(Integer points) {
        assertThatThrownBy(() -> transactionService.create(new LoyaltyTransactionRequest(
                account.getId(), LoyaltyTransactionType.EARN, points, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_TRANSACTION_POINTS_REQUIRED)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(transactionRepository);
        assertThat(account.getPointBalance()).isEqualTo(100);
    }

    @Test
    void createRejectsUnknownAccount() {
        UUID unknownAccountId = UUID.randomUUID();
        when(accountRepository.findByIdAndIsDeletedFalse(unknownAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(new LoyaltyTransactionRequest(
                unknownAccountId, LoyaltyTransactionType.EARN, 10, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_ACCOUNT_NOT_FOUND);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getAllFiltersByAccountWhenProvided() {
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setLoyaltyAccount(account);
        transaction.setType(LoyaltyTransactionType.EARN);
        transaction.setPoints(10);
        when(transactionRepository.findAllByLoyaltyAccountIdAndIsDeletedFalse(account.getId()))
                .thenReturn(List.of(transaction));

        assertThat(transactionService.getAll(account.getId())).hasSize(1);
        verify(transactionRepository, never()).findAllByIsDeletedFalse();
    }

    @Test
    void getAllWithoutAccountIdReturnsEveryTransaction() {
        when(transactionRepository.findAllByIsDeletedFalse()).thenReturn(List.of(transaction(10), transaction(-5)));

        assertThat(transactionService.getAll(null)).extracting(LoyaltyTransactionResponse::points).containsExactly(10, -5);
        verify(transactionRepository, never()).findAllByLoyaltyAccountIdAndIsDeletedFalse(any());
    }

    @Test
    void getByIdReturnsTransaction() {
        LoyaltyTransaction transaction = transaction(50);
        when(transactionRepository.findByIdAndIsDeletedFalse(transaction.getId())).thenReturn(Optional.of(transaction));

        LoyaltyTransactionResponse response = transactionService.getById(transaction.getId());

        assertThat(response.id()).isEqualTo(transaction.getId());
        assertThat(response.loyaltyAccountId()).isEqualTo(account.getId());
    }

    @Test
    void getByIdRejectsMissingTransaction() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(id))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_TRANSACTION_NOT_FOUND);
    }

    private LoyaltyTransaction transaction(int points) {
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setLoyaltyAccount(account);
        transaction.setType(LoyaltyTransactionType.EARN);
        transaction.setPoints(points);
        return transaction;
    }
}
