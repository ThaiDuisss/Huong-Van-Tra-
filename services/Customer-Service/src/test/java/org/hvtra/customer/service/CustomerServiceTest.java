package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.infrastructure.client.OrderClient;
import org.hvtra.customer.infrastructure.gateway.OrderGateway;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerType;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private static final LocalDateTime SOLD_AT = LocalDateTime.of(2026, 8, 15, 10, 0);

    private CustomerRepository customerRepository;
    private CustomerTierRepository tierRepository;
    private LoyaltyAccountService loyaltyAccountService;
    private LoyaltyPointRuleService loyaltyPointRuleService;
    private LoyaltyTransactionRepository loyaltyTransactionRepository;
    private OrderGateway orderGateway;
    private TierQualificationService tierQualificationService;
    private TierAssignmentService tierAssignmentService;
    private LoyaltyTierService loyaltyTierService;
    private CustomerServiceImpl customerService;

    private CustomerTier member;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        tierRepository = mock(CustomerTierRepository.class);
        loyaltyAccountService = mock(LoyaltyAccountService.class);
        loyaltyPointRuleService = mock(LoyaltyPointRuleService.class);
        loyaltyTransactionRepository = mock(LoyaltyTransactionRepository.class);
        orderGateway = mock(OrderGateway.class);
        tierQualificationService = mock(TierQualificationService.class);
        tierAssignmentService = mock(TierAssignmentService.class);
        loyaltyTierService = mock(LoyaltyTierService.class);
        customerService = new CustomerServiceImpl(
                customerRepository,
                tierRepository,
                new CustomerMapper(),
                loyaltyAccountService,
                loyaltyPointRuleService,
                loyaltyTransactionRepository,
                orderGateway,
                tierQualificationService,
                tierAssignmentService,
                loyaltyTierService
        );

        member = new CustomerTier();
        member.setId(UUID.randomUUID());
        member.setCode("MEMBER");
        member.setName("Thành viên");
        when(tierQualificationService.resolveQualifiedTier(BigDecimal.ZERO, 0)).thenReturn(member);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            if (customer.getId() == null) {
                customer.setId(UUID.randomUUID());
            }
            return customer;
        });
    }

    @Test
    void createGeneratesCodeAssignsInitialTierAndOpensLoyaltyAccount() {
        CustomerResponse response = customerService.create(new CustomerRequest(
                null, "Nguyễn Văn A", "0901234567", null, null, null, null, null, null, null, null, null));

        assertThat(response.customerCode()).matches("[A-Z]+-\\d{4}");
        assertThat(response.customerType()).isEqualTo(CustomerType.INDIVIDUAL);
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(response.tierCode()).isEqualTo("MEMBER");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        verify(tierAssignmentService).assignInitialTier(eq(saved.getValue()), eq(member), any(LocalDateTime.class));
        verify(loyaltyAccountService).create(new LoyaltyAccountRequest(saved.getValue().getId(), 0));
    }

    @Test
    void createKeepsProvidedCodeAndRejectsDuplicateEmail() {
        when(customerRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(new CustomerRequest(
                "KH-1", "Nguyễn Văn A", "0901234567", "a@example.com", null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_EMAIL_EXISTS);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCustomerCode() {
        when(customerRepository.existsByCustomerCode("KH-1")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(new CustomerRequest(
                "KH-1", "Nguyễn Văn A", "0901234567", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_CODE_EXISTS);
    }

    @Test
    void updateRejectsPhoneUsedByAnotherCustomer() {
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepository.existsByPhoneAndIdNot("0909999999", customer.getId())).thenReturn(true);

        assertThatThrownBy(() -> customerService.update(customer.getId(), new CustomerRequest(
                null, "Nguyễn Văn A", "0909999999", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_PHONE_EXISTS)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateRejectsUnknownTier() {
        Customer customer = customer();
        UUID unknownTierId = UUID.randomUUID();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(tierRepository.findByIdAndIsDeletedFalse(unknownTierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(customer.getId(), new CustomerRequest(
                null, "Nguyễn Văn A", "0901234567", null, null, null, unknownTierId, null, null, null, null, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_TIER_NOT_FOUND);
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
    void updatePurchaseRecordsPurchaseAndEarnsPointsFromApplicableRule() {
        Customer customer = customer();
        customer.setTier(member);
        UUID orderId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(orderGateway.getOrderById(orderId)).thenReturn(order(orderId, customer.getId(), 500_000));
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(loyaltyPointRuleService.findApplicableRule(eq(member.getId()), eq(BigDecimal.valueOf(500_000)), any()))
                .thenReturn(Optional.of(pointRule(10_000, 1, "1.5")));
        when(loyaltyAccountService.getByCustomerId(customer.getId()))
                .thenReturn(new LoyaltyAccountResponse(accountId, customer.getId(), "NVA-1024", "Nguyễn Văn A", 0));

        CustomerResponse response = customerService.updatePurchase(orderId);

        assertThat(response.id()).isEqualTo(customer.getId());
        verify(loyaltyTierService).recordPurchase(new OrderCompletedEvent(
                orderId, "DH-1", customer.getId(), BigDecimal.valueOf(500_000), SOLD_AT));
        verify(loyaltyAccountService).updateBalance(new LoyaltyTransactionRequest(
                accountId, LoyaltyTransactionType.EARN, 75, LoyaltyReferenceType.ORDER, orderId));
    }

    @Test
    void updatePurchaseDoesNotEarnPointsTwiceForSameOrder() {
        Customer customer = customer();
        UUID orderId = UUID.randomUUID();
        when(orderGateway.getOrderById(orderId)).thenReturn(order(orderId, customer.getId(), 500_000));
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(loyaltyPointRuleService.findApplicableRule(any(), any(), any()))
                .thenReturn(Optional.of(pointRule(10_000, 1, "1")));
        when(loyaltyTransactionRepository.existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
                LoyaltyTransactionType.EARN, LoyaltyReferenceType.ORDER, orderId)).thenReturn(true);

        customerService.updatePurchase(orderId);

        verify(loyaltyAccountService, never()).updateBalance(any());
    }

    @Test
    void updatePurchaseEarnsNoPointsWithoutApplicableRule() {
        Customer customer = customer();
        UUID orderId = UUID.randomUUID();
        when(orderGateway.getOrderById(orderId)).thenReturn(order(orderId, customer.getId(), 500_000));
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(loyaltyPointRuleService.findApplicableRule(any(), any(), any())).thenReturn(Optional.empty());

        customerService.updatePurchase(orderId);

        verify(loyaltyTierService).recordPurchase(any());
        verify(loyaltyAccountService, never()).updateBalance(any());
    }

    @Test
    void updatePurchaseRejectsOrderWithoutCustomer() {
        UUID orderId = UUID.randomUUID();
        when(orderGateway.getOrderById(orderId)).thenReturn(order(orderId, null, 500_000));

        assertThatThrownBy(() -> customerService.updatePurchase(orderId))
                .isInstanceOf(CustomerServiceException.class)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(loyaltyTierService, loyaltyAccountService);
    }

    @Test
    void updatePurchaseRejectsNegativeAmount() {
        UUID orderId = UUID.randomUUID();
        when(orderGateway.getOrderById(orderId)).thenReturn(order(orderId, UUID.randomUUID(), -1));

        assertThatThrownBy(() -> customerService.updatePurchase(orderId))
                .isInstanceOf(CustomerServiceException.class);
        verifyNoInteractions(loyaltyTierService);
    }

    @Test
    void findAllMapsPagedCustomersToResponse() {
        Customer customer = customer();
        PageRequest pageable = PageRequest.of(0, 20);
        when(customerRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(customer), pageable, 1));

        PageResponse<CustomerResponse> page = customerService.findAll(new CustomerFilterRequest(
                "Nguyễn", CustomerStatus.ACTIVE, null, null, null, null, null, null, null, null, null, null, null, null), pageable);

        assertThat(page.content()).singleElement()
                .extracting(CustomerResponse::customerCode)
                .isEqualTo("NVA-1024");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    void findAllReturnsEmptyPageWhenNothingMatches() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(customerRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<CustomerResponse> page = customerService.findAll(new CustomerFilterRequest(
                "Không tồn tại", null, null, null, null, null, null, null, null, null, null, null, null, null), pageable);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void getByIdReturnsCustomer() {
        Customer customer = customer();
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(customer.getId());

        assertThat(response.id()).isEqualTo(customer.getId());
        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
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

    private OrderClient.OrderResponse order(UUID orderId, UUID customerId, int finalAmount) {
        return new OrderClient.OrderResponse(orderId, "DH-1", customerId,
                BigDecimal.valueOf(finalAmount), BigDecimal.valueOf(finalAmount), SOLD_AT);
    }

    private LoyaltyPointRule pointRule(int spendAmountPerBlock, int pointsPerBlock, String multiplier) {
        LoyaltyPointRule rule = new LoyaltyPointRule();
        rule.setId(UUID.randomUUID());
        rule.setSpendAmountPerBlock(BigDecimal.valueOf(spendAmountPerBlock));
        rule.setPointsPerBlock(pointsPerBlock);
        rule.setPointMultiplier(new BigDecimal(multiplier));
        return rule;
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
}
