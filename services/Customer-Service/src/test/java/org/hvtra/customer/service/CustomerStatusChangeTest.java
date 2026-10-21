package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.ChangeCustomerStatusRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.infrastructure.gateway.OrderGateway;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

class CustomerStatusChangeTest {

    private CustomerRepository customerRepository;
    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        customerService = new CustomerServiceImpl(
                customerRepository,
                mock(CustomerTierRepository.class),
                new CustomerMapper(),
                mock(LoyaltyAccountService.class),
                mock(LoyaltyPointRuleService.class),
                mock(LoyaltyTransactionRepository.class),
                mock(OrderGateway.class),
                mock(TierQualificationService.class),
                mock(TierAssignmentService.class),
                mock(LoyaltyTierService.class)
        );
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void changeStatusUpdatesOnlyStatusAndKeepsCustomerUndeleted() {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.changeStatus(
                id, new ChangeCustomerStatusRequest(CustomerStatus.INACTIVE));

        assertThat(response.status()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(customer.getIsDeleted()).isNotEqualTo(Boolean.TRUE);
        assertThat(customer.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(customer.getPhone()).isEqualTo("0901234567");
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("250000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(3);
        verify(customerRepository).save(customer);
    }

    @Test
    void changeStatusCanReactivateCustomer() {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        customer.setStatus(CustomerStatus.INACTIVE);
        when(customerRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.changeStatus(
                id, new ChangeCustomerStatusRequest(CustomerStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void changeStatusRejectsMissingOrDeletedCustomer() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.changeStatus(
                id, new ChangeCustomerStatusRequest(CustomerStatus.INACTIVE)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_NOT_FOUND)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createReportsDuplicatePhoneAsConflictInsteadOfServerError() {
        when(customerRepository.existsByPhone("0901234567")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(new CustomerRequest(
                "KH-1", "Nguyễn Văn A", "0901234567", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_PHONE_EXISTS)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateKeepsPurchaseStatisticsWhenRequestOmitsThem() {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.update(id, new CustomerRequest(
                null, "Nguyễn Văn B", "0901234567", null, null, null, null, null, null, null, null, null));

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
        assertThat(response.totalOrderCount()).isEqualTo(3);
        assertThat(response.totalSpent()).isEqualByComparingTo("250000");
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setCustomerCode("NVA-1024");
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0901234567");
        customer.setTotalSpent(BigDecimal.valueOf(250_000));
        customer.setTotalOrderCount(3);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }
}
