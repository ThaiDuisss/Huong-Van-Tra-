package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.LoyaltyAccountRepository;
import org.hvtra.customer.service.impl.LoyaltyAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyAccountServiceTest {

    private LoyaltyAccountRepository accountRepository;
    private CustomerRepository customerRepository;
    private LoyaltyTransactionService transactionService;
    private LoyaltyAccountServiceImpl accountService;
    private Customer customer;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LoyaltyAccountRepository.class);
        customerRepository = mock(CustomerRepository.class);
        transactionService = mock(LoyaltyTransactionService.class);
        accountService = new LoyaltyAccountServiceImpl(accountRepository, customerRepository, new CustomerMapper(), transactionService);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerCode("NVA-1024");
        customer.setFullName("Nguyễn Văn A");
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createOpensAccountWithZeroPointsWhenBalanceOmitted() {
        LoyaltyAccountResponse response = accountService.create(new LoyaltyAccountRequest(customer.getId(), null));

        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.customerCode()).isEqualTo("NVA-1024");
        assertThat(response.customerName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.pointBalance()).isZero();
    }

    @Test
    void createRejectsSecondAccountForSameCustomer() {
        when(accountRepository.existsByCustomerId(customer.getId())).thenReturn(true);

        assertThatThrownBy(() -> accountService.create(new LoyaltyAccountRequest(customer.getId(), 0)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_ACCOUNT_CUSTOMER_EXISTS)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void getByCustomerIdReturnsAccount() {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setId(UUID.randomUUID());
        account.setCustomer(customer);
        account.setPointBalance(320);
        when(accountRepository.findByCustomerIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(account));

        LoyaltyAccountResponse response = accountService.getByCustomerId(customer.getId());

        assertThat(response.id()).isEqualTo(account.getId());
        assertThat(response.pointBalance()).isEqualTo(320);
    }

    @Test
    void getByCustomerIdRejectsCustomerWithoutAccount() {
        when(accountRepository.findByCustomerIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getByCustomerId(customer.getId()))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_ACCOUNT_NOT_FOUND);
    }

    @Test
    void updateBalanceRecordsTransactionThenReturnsRefreshedAccount() {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setId(UUID.randomUUID());
        account.setCustomer(customer);
        account.setPointBalance(150);
        when(accountRepository.findByIdAndIsDeletedFalse(account.getId())).thenReturn(Optional.of(account));
        LoyaltyTransactionRequest request = new LoyaltyTransactionRequest(
                account.getId(), LoyaltyTransactionType.EARN, 50, LoyaltyReferenceType.ORDER, UUID.randomUUID());

        LoyaltyAccountResponse response = accountService.updateBalance(request);

        assertThat(response.id()).isEqualTo(account.getId());
        assertThat(response.pointBalance()).isEqualTo(150);
        var order = inOrder(transactionService, accountRepository);
        order.verify(transactionService).create(request);
        order.verify(accountRepository).findByIdAndIsDeletedFalse(account.getId());
    }
}
