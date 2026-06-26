package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;
import org.hvtra.customer.repository.LoyaltyPointRuleRepository;
import org.hvtra.customer.service.LoyaltyPointRuleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyPointRuleServiceImpl implements LoyaltyPointRuleService {
    private static final PageRequest FIRST_RESULT = PageRequest.of(0, 1);

    private final LoyaltyPointRuleRepository loyaltyPointRuleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LoyaltyPointRule> getAll() {
        return loyaltyPointRuleRepository.findAllByIsDeletedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyPointRule getById(UUID id) {
        return findRule(id);
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
    public LoyaltyPointRule create(LoyaltyPointRule rule) {
        ensureCodeAvailable(rule.getCode(), null);
        return loyaltyPointRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public LoyaltyPointRule update(UUID id, LoyaltyPointRule rule) {
        LoyaltyPointRule existingRule = findRule(id);
        ensureCodeAvailable(rule.getCode(), id);
        updateFields(existingRule, rule);
        return loyaltyPointRuleRepository.save(existingRule);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LoyaltyPointRule rule = findRule(id);
        rule.setIsDeleted(true);
        loyaltyPointRuleRepository.save(rule);
    }

    private LoyaltyPointRule findRule(UUID id) {
        return loyaltyPointRuleRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound(Message.LOYALTY_POINT_RULE_NOT_FOUND));
    }

    private void updateFields(LoyaltyPointRule target, LoyaltyPointRule source) {
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setTier(source.getTier());
        target.setSpendAmountPerBlock(source.getSpendAmountPerBlock());
        target.setPointsPerBlock(source.getPointsPerBlock());
        target.setMinOrderAmount(source.getMinOrderAmount());
        target.setEarnAfterDiscount(source.getEarnAfterDiscount());
        target.setAllowCouponUsage(source.getAllowCouponUsage());
        target.setPointMultiplier(source.getPointMultiplier());
        target.setPriority(source.getPriority());
        target.setEffectiveFrom(source.getEffectiveFrom());
        target.setEffectiveTo(source.getEffectiveTo());
        target.setStatus(source.getStatus());
    }

    private void ensureCodeAvailable(String code, UUID currentId) {
        boolean exists = currentId == null
                ? loyaltyPointRuleRepository.existsByCodeAndIsDeletedFalse(code)
                : loyaltyPointRuleRepository.existsByCodeAndIdNotAndIsDeletedFalse(code, currentId);
        if (exists) {
            throw conflict(Message.LOYALTY_POINT_RULE_CODE_EXISTS);
        }
    }

    private CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private CustomerServiceException conflict(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_CONFLICT, message, HttpStatus.CONFLICT);
    }
}
