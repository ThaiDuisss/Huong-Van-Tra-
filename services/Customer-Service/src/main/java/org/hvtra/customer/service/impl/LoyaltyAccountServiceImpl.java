package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.LoyaltyAccountRepository;
import org.hvtra.customer.service.LoyaltyAccountService;
import org.hvtra.customer.service.LoyaltyTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hvtra.customer.exception.CustomerServiceException.notFound;
import static org.hvtra.customer.exception.CustomerServiceException.conflict;

@Service
@RequiredArgsConstructor
public class LoyaltyAccountServiceImpl implements LoyaltyAccountService {
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final LoyaltyTransactionService loyaltyTransactionService;

    @Override
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getByCustomerId(UUID customerId) {
        return customerMapper.toResponse(loyaltyAccountRepository.findByCustomerIdAndIsDeletedFalse(customerId)
                .orElseThrow(() -> notFound(Message.LOYALTY_ACCOUNT_NOT_FOUND)));
    }

    @Override
    @Transactional
    public LoyaltyAccountResponse create(LoyaltyAccountRequest request) {
        ensureCustomerAvailable(request.customerId());
        LoyaltyAccount account = new LoyaltyAccount();
        customerMapper.update(account, request, findCustomer(request.customerId()));
        return customerMapper.toResponse(loyaltyAccountRepository.save(account));
    }

    @Override
    @Transactional
    public LoyaltyAccountResponse updateBalance(LoyaltyTransactionRequest request){
        loyaltyTransactionService.create(request);

        return customerMapper.toResponse(findAccount(request.loyaltyAccountId()));
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

    private void ensureCustomerAvailable(UUID customerId) {
        if (loyaltyAccountRepository.existsByCustomerId(customerId)) {
            throw conflict(Message.LOYALTY_ACCOUNT_CUSTOMER_EXISTS);
        }
    }
}
