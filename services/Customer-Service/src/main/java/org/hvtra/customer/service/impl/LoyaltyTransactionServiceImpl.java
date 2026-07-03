package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.repository.LoyaltyAccountRepository;
import org.hvtra.customer.repository.LoyaltyTransactionRepository;
import org.hvtra.customer.service.LoyaltyTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyTransactionServiceImpl implements LoyaltyTransactionService {
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LoyaltyTransactionResponse> getAll(UUID loyaltyAccountId) {
        List<LoyaltyTransaction> transactions = loyaltyAccountId == null
                ? loyaltyTransactionRepository.findAllByIsDeletedFalse()
                : loyaltyTransactionRepository.findAllByLoyaltyAccountIdAndIsDeletedFalse(loyaltyAccountId);
        return transactions.stream().map(customerMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyTransactionResponse getById(UUID id) {
        return customerMapper.toResponse(findTransaction(id));
    }

    @Override
    @Transactional
    public LoyaltyTransactionResponse create(LoyaltyTransactionRequest request) {
        validatePoints(request.points());
        LoyaltyAccount account = findAccount(request.loyaltyAccountId());
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        customerMapper.update(transaction, request, account);
        applyBalanceDelta(account, request.points());
        return customerMapper.toResponse(loyaltyTransactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public LoyaltyTransactionResponse update(UUID id, LoyaltyTransactionRequest request) {
        validatePoints(request.points());
        LoyaltyTransaction transaction = findTransaction(id);
        LoyaltyAccount oldAccount = transaction.getLoyaltyAccount();
        int oldPoints = transaction.getPoints();
        LoyaltyAccount newAccount = findAccount(request.loyaltyAccountId());

        applyBalanceDelta(oldAccount, -oldPoints);
        applyBalanceDelta(newAccount, request.points());
        customerMapper.update(transaction, request, newAccount);

        return customerMapper.toResponse(loyaltyTransactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LoyaltyTransaction transaction = findTransaction(id);
        applyBalanceDelta(transaction.getLoyaltyAccount(), -transaction.getPoints());
        transaction.setIsDeleted(true);
        loyaltyTransactionRepository.save(transaction);
    }

    //======================================= Helper classes =======================================

    private LoyaltyTransaction findTransaction(UUID id) {
        return loyaltyTransactionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.LOYALTY_TRANSACTION_NOT_FOUND));
    }

    private LoyaltyAccount findAccount(UUID id) {
        return loyaltyAccountRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.LOYALTY_ACCOUNT_NOT_FOUND));
    }

    private void validatePoints(Integer points) {
        if (points == null || points == 0) {
            throw new CustomerServiceException(ErrorCode.CUSTOMER_INVALID_REQUEST,
                    Message.LOYALTY_TRANSACTION_POINTS_REQUIRED, HttpStatus.BAD_REQUEST);
        }
    }

    private void applyBalanceDelta(LoyaltyAccount account, int delta) {
        account.setPointBalance(account.getPointBalance() + delta);
        loyaltyAccountRepository.save(account);
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }
}
