package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.CustomerService;
import org.hvtra.customer.service.LoyaltyAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerTierRepository customerTierRepository;
    private final CustomerMapper customerMapper;
    private final LoyaltyAccountService loyaltyAccountService;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        return customerRepository.findAllByIsDeletedFalse().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String keyword) {
        if (!hasText(keyword)) {
            return getAll();
        }
        return customerRepository.searchByKeyword(keyword.trim()).stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        CustomerRequest normalizedRequest = normalizeCreateRequest(request);
        ensureUniqueFields(normalizedRequest, null);
        Customer customer = new Customer();
        customerMapper.update(customer, normalizedRequest, findTier(normalizedRequest.tierId()));
        CustomerResponse response = customerMapper.toResponse(customerRepository.save(customer));

        LoyaltyAccountRequest loyaltyAccountRequest = new LoyaltyAccountRequest(response.id(), 0);
        loyaltyAccountService.create(loyaltyAccountRequest);

        return response;
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findCustomer(id);
        CustomerRequest normalizedRequest = normalizeUpdateRequest(request, customer);
        ensureUniqueFields(normalizedRequest, id);
        customerMapper.update(customer, normalizedRequest, findTier(normalizedRequest.tierId()));
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Customer customer = findCustomer(id);
        customer.setIsDeleted(true);
        customerRepository.save(customer);
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
    }

    private CustomerTier findTier(UUID tierId) {
        if (tierId == null) {
            return null;
        }
        return customerTierRepository.findByIdAndIsDeletedFalse(tierId)
                .orElseThrow(() -> notFound(Message.CUSTOMER_TIER_NOT_FOUND));
    }

    private void ensureUniqueFields(CustomerRequest request, UUID currentId) {
        if (hasText(request.customerCode()) && existsCustomerCode(request.customerCode(), currentId)) {
            throw conflict(Message.CUSTOMER_CODE_EXISTS);
        }
        if (existsPhone(request.phone(), currentId)) {
            throw conflict(Message.CUSTOMER_PHONE_EXISTS);
        }
        if (request.email() != null && !request.email().isBlank() && existsEmail(request.email(), currentId)) {
            throw conflict(Message.CUSTOMER_EMAIL_EXISTS);
        }
    }

    private boolean existsCustomerCode(String customerCode, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByCustomerCode(customerCode)
                : customerRepository.existsByCustomerCodeAndIdNot(customerCode, currentId);
    }

    private boolean existsPhone(String phone, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByPhone(phone)
                : customerRepository.existsByPhoneAndIdNot(phone, currentId);
    }

    private boolean existsEmail(String email, UUID currentId) {
        return currentId == null
                ? customerRepository.existsByEmail(email)
                : customerRepository.existsByEmailAndIdNot(email, currentId);
    }

    private CustomerRequest normalizeCreateRequest(CustomerRequest request) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode() : generateCustomerCode(),
                request.fullName(),
                request.phone(),
                request.email(),
                request.dateOfBirth(),
                request.tierId(),
                request.creditLimit(),
                request.status() != null ? request.status() : CustomerStatus.ACTIVE
        );
    }

    private CustomerRequest normalizeUpdateRequest(CustomerRequest request, Customer customer) {
        return new CustomerRequest(
                hasText(request.customerCode()) ? request.customerCode() : customer.getCustomerCode(),
                request.fullName(),
                request.phone(),
                request.email(),
                request.dateOfBirth(),
                request.tierId(),
                request.creditLimit(),
                request.status() != null ? request.status() : customer.getStatus()
        );
    }

    private String generateCustomerCode() {
        String customerCode;
        do {
            customerCode = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (customerRepository.existsByCustomerCode(customerCode));
        return customerCode;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private CustomerServiceException conflict(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_CONFLICT, message, HttpStatus.CONFLICT);
    }
}
