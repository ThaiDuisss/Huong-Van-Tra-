package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.LoyaltyAccountRepository;
import org.hvtra.customer.service.LoyaltyAccountService;
import org.hvtra.customer.service.LoyaltyTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyAccountServiceImpl implements LoyaltyAccountService {
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final LoyaltyTransactionService loyaltyTransactionService;

    @Override
    @Transactional(readOnly = true)
    public List<LoyaltyAccountResponse> getAll() {
        return loyaltyAccountRepository.findAllByIsDeletedFalse().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getById(UUID id) {
        return customerMapper.toResponse(findAccount(id));
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getByCustomerId(UUID customerId) {
        return customerMapper.toResponse(loyaltyAccountRepository.findByCustomerIdAndIsDeletedFalse(customerId)
                .orElseThrow(() -> notFound(Message.LOYALTY_ACCOUNT_NOT_FOUND)));
    }

    @Override
    @Transactional
    public LoyaltyAccountResponse create(LoyaltyAccountRequest request) {
        ensureCustomerAvailable(request.customerId(), null);
        LoyaltyAccount account = new LoyaltyAccount();
        customerMapper.update(account, request, findCustomer(request.customerId()));
        return customerMapper.toResponse(loyaltyAccountRepository.save(account));
    }

    @Override
    @Transactional
    public LoyaltyAccountResponse update(UUID id, LoyaltyAccountRequest request) {
        LoyaltyAccount account = findAccount(id);
        ensureCustomerAvailable(request.customerId(), id);
        customerMapper.update(account, request, findCustomer(request.customerId()));
        return customerMapper.toResponse(loyaltyAccountRepository.save(account));
    }

    @Override
    @Transactional
    public LoyaltyAccountResponse updateBalance(LoyaltyTransactionRequest request){
        loyaltyTransactionService.create(request);

        return customerMapper.toResponse(findAccount(request.loyaltyAccountId()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LoyaltyAccount account = findAccount(id);
        account.setIsDeleted(true);
        loyaltyAccountRepository.save(account);
    }


    //======================================= Helper classes =======================================

    private LoyaltyAccount findAccount(UUID id) {
        return loyaltyAccountRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.LOYALTY_ACCOUNT_NOT_FOUND));
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
    }

    private void ensureCustomerAvailable(UUID customerId, UUID currentId) {
        boolean exists = currentId == null
                ? loyaltyAccountRepository.existsByCustomerId(customerId)
                : loyaltyAccountRepository.existsByCustomerIdAndIdNot(customerId, currentId);
        if (exists) {
            throw new CustomerServiceException(ErrorCode.CUSTOMER_CONFLICT,
                    Message.LOYALTY_ACCOUNT_CUSTOMER_EXISTS, HttpStatus.CONFLICT);
        }
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }
}
