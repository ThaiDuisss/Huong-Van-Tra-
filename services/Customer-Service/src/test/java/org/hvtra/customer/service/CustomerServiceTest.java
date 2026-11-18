package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerLookupResponse;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private CustomerRepository customerRepository;
    private TierQualificationService tierQualificationService;
    private CustomerServiceImpl customerService;

    private CustomerTier member;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        tierQualificationService = mock(TierQualificationService.class);
        customerService = new CustomerServiceImpl(customerRepository, new CustomerMapper(), tierQualificationService);

        member = tier("MEMBER", 0, 0);
        when(tierQualificationService.upgradeIfQualified(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setTier(member);
            return true;
        });
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            if (customer.getId() == null) {
                customer.setId(UUID.randomUUID());
            }
            return customer;
        });
    }

    @Test
    void createGeneratesCodeStartsFromZeroAndAssignsLowestQualifiedTier() {
        CustomerResponse response = customerService.create(
                new CustomerRequest(null, "Nguyễn Văn A", "0901234567", null, null, null));

        assertThat(response.customerCode()).matches("[A-Z]+-\\d{4}");
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(response.tierCode()).isEqualTo("MEMBER");
        assertThat(response.totalSpent()).isEqualByComparingTo("0");
        assertThat(response.totalOrderCount()).isZero();
        assertThat(response.pointBalance()).isZero();
        verify(tierQualificationService).upgradeIfQualified(any(Customer.class));
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(customerRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(
                new CustomerRequest("KH-1", "Nguyễn Văn A", "0901234567", "a@example.com", null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_EMAIL_EXISTS);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCustomerCode() {
        when(customerRepository.existsByCustomerCode("KH-1")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(
                new CustomerRequest("KH-1", "Nguyễn Văn A", "0901234567", null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_CODE_EXISTS);
    }

    @Test
    void updateRejectsPhoneUsedByAnotherCustomer() {
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepository.existsByPhoneAndIdNot("0909999999", customer.getId())).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(customer.getId(),
                new CustomerRequest(null, "Nguyễn Văn A", "0909999999", null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_PHONE_EXISTS)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateNeverTouchesTier() {
        Customer customer = customer();
        CustomerTier gold = tier("GOLD", 5_000_000, 10);
        customer.setTier(gold);
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.update(customer.getId(),
                new CustomerRequest(null, "Nguyễn Văn B", "0901234567", null, null, null));

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
        assertThat(response.tierCode()).isEqualTo("GOLD");
        verify(tierQualificationService, never()).upgradeIfQualified(any());
    }

    @Test
    void deleteSoftDeletesCustomer() {
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));

        customerService.delete(customer.getId());

        assertThat(customer.getIsDeleted()).isTrue();
        verify(customerRepository).save(customer);
    }

    @Test
    void findAllMapsPagedCustomersWithoutNextTier() {
        Customer customer = customer();
        PageRequest pageable = PageRequest.of(0, 20);
        when(customerRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(customer), pageable, 1));

        PageResponse<CustomerResponse> page = customerService.findAll(new CustomerFilterRequest(
                "Nguyễn", CustomerStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null, null), pageable);

        assertThat(page.content()).singleElement()
                .satisfies(response -> {
                    assertThat(response.customerCode()).isEqualTo("NVA-1024");
                    assertThat(response.nextTier()).isNull();
                });
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void getByIdIncludesNextTierAndRemainingAmounts() {
        Customer customer = customer();
        customer.setTier(member);
        customer.setTotalSpent(BigDecimal.valueOf(400_000));
        customer.setTotalOrderCount(1);
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(tierQualificationService.findNextTier(customer)).thenReturn(Optional.of(silver));

        CustomerResponse response = customerService.getById(customer.getId());

        assertThat(response.nextTier().code()).isEqualTo("SILVER");
        assertThat(response.remainingSpending()).isEqualByComparingTo("600000");
        assertThat(response.remainingOrderCount()).isEqualTo(2);
    }

    @Test
    void getByIdAtHighestTierHasNoNextTier() {
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(tierQualificationService.findNextTier(customer)).thenReturn(Optional.empty());

        CustomerResponse response = customerService.getById(customer.getId());

        assertThat(response.nextTier()).isNull();
        assertThat(response.remainingSpending()).isNull();
    }

    @Test
    void getByIdRejectsMissingCustomer() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(customerRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(id))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_NOT_FOUND)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void lookupReturnsOnlyIdNameCodeAndPhone() {
        Customer customer = customer();
        when(customerRepository.lookup(eq("0901"), eq(CustomerStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(customer));

        List<CustomerLookupResponse> result = customerService.lookup(" 0901 ", 20);

        assertThat(result).containsExactly(new CustomerLookupResponse(
                customer.getId(), "Nguyễn Văn A", "NVA-1024", "0901234567"));
    }

    @Test
    void lookupWithBlankKeywordSearchesAll() {
        when(customerRepository.lookup(isNull(), eq(CustomerStatus.ACTIVE), any(Pageable.class))).thenReturn(List.of());

        assertThat(customerService.lookup("  ", 20)).isEmpty();
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerCode("NVA-1024");
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0901234567");
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    private CustomerTier tier(String code, int minSpending, int minOrderCount) {
        CustomerTier tier = new CustomerTier();
        tier.setId(UUID.randomUUID());
        tier.setCode(code);
        tier.setName(code);
        tier.setMinSpending(BigDecimal.valueOf(minSpending));
        tier.setMinOrderCount(minOrderCount);
        tier.setColorCode("#000000");
        return tier;
    }
}
