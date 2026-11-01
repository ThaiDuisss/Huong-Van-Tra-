package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyPointRuleRequest;
import org.hvtra.customer.dto.response.LoyaltyPointRuleResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.LoyaltyPointRuleRepository;
import org.hvtra.customer.service.LoyaltyPointRuleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hvtra.customer.exception.CustomerServiceException.conflict;
import static org.hvtra.customer.exception.CustomerServiceException.notFound;

@Service
@RequiredArgsConstructor
public class LoyaltyPointRuleServiceImpl implements LoyaltyPointRuleService {
    private static final PageRequest FIRST_RESULT = PageRequest.of(0, 1);

    private final LoyaltyPointRuleRepository loyaltyPointRuleRepository;
    private final CustomerTierRepository customerTierRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LoyaltyPointRuleResponse> getAll() {
        return loyaltyPointRuleRepository.findAllByIsDeletedFalse().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyPointRuleResponse getById(UUID id) {
        return customerMapper.toResponse(findRule(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoyaltyPointRule> findApplicableRule(UUID tierId, BigDecimal orderAmount, Instant effectiveAt) {
        BigDecimal amount = orderAmount == null ? BigDecimal.ZERO : orderAmount;
        Instant at = effectiveAt == null ? Instant.now() : effectiveAt;
        return loyaltyPointRuleRepository.findApplicableRules(
                        tierId,
                        amount,
                        at,
                        LoyaltyPointRuleStatus.ACTIVE,
                        FIRST_RESULT
                ).stream()
                .findFirst();
    }

    @Override
    @Transactional
    public LoyaltyPointRuleResponse create(LoyaltyPointRuleRequest request) {
        ensureCodeAvailable(request.code(), null);
        LoyaltyPointRule rule = new LoyaltyPointRule();
        customerMapper.update(rule, request, findTier(request.tierId()));
        return customerMapper.toResponse(loyaltyPointRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public LoyaltyPointRuleResponse update(UUID id, LoyaltyPointRuleRequest request) {
        LoyaltyPointRule rule = findRule(id);
        ensureCodeAvailable(request.code(), id);
        customerMapper.update(rule, request, findTier(request.tierId()));
        return customerMapper.toResponse(loyaltyPointRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LoyaltyPointRule rule = findRule(id);
        rule.setIsDeleted(true);
        loyaltyPointRuleRepository.save(rule);
    }

    //======================================= Helper classes =======================================

    private LoyaltyPointRule findRule(UUID id) {
        return loyaltyPointRuleRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.LOYALTY_POINT_RULE_NOT_FOUND));
    }

    // Quy tắc không gắn hạng thì áp dụng chung cho mọi hạng.
    private CustomerTier findTier(UUID tierId) {
        if (tierId == null) {
            return null;
        }
        return customerTierRepository.findByIdAndIsDeletedFalse(tierId)
                .orElseThrow(() -> notFound(Message.CUSTOMER_TIER_NOT_FOUND));
    }

    private void ensureCodeAvailable(String code, UUID currentId) {
        boolean exists = currentId == null
                ? loyaltyPointRuleRepository.existsByCodeAndIsDeletedFalse(code)
                : loyaltyPointRuleRepository.existsByCodeAndIdNotAndIsDeletedFalse(code, currentId);
        if (exists) {
            throw conflict(Message.LOYALTY_POINT_RULE_CODE_EXISTS);
        }
    }
}
