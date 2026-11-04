package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.LoyaltyPointRuleRequest;
import org.hvtra.customer.dto.response.LoyaltyPointRuleResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.LoyaltyPointRuleRepository;
import org.hvtra.customer.service.impl.LoyaltyPointRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
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

class LoyaltyPointRuleServiceTest {

    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-01-01T00:00:00Z");

    private LoyaltyPointRuleRepository ruleRepository;
    private CustomerTierRepository tierRepository;
    private LoyaltyPointRuleServiceImpl ruleService;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(LoyaltyPointRuleRepository.class);
        tierRepository = mock(CustomerTierRepository.class);
        ruleService = new LoyaltyPointRuleServiceImpl(ruleRepository, tierRepository, new CustomerMapper());
        when(ruleRepository.save(any(LoyaltyPointRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWithoutTierAppliesDatabaseDefaults() {
        LoyaltyPointRuleResponse response = ruleService.create(request("RULE-1", null));

        assertThat(response.tierId()).isNull();
        assertThat(response.minOrderAmount()).isEqualByComparingTo("0");
        assertThat(response.earnAfterDiscount()).isTrue();
        assertThat(response.allowCouponUsage()).isTrue();
        assertThat(response.pointMultiplier()).isEqualByComparingTo("1");
        assertThat(response.priority()).isEqualTo(100);
        verify(tierRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void createLinksRuleToRequestedTier() {
        CustomerTier silver = new CustomerTier();
        silver.setId(UUID.randomUUID());
        silver.setCode("SILVER");
        silver.setName("Bạc");
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));

        LoyaltyPointRuleResponse response = ruleService.create(request("RULE-1", silver.getId()));

        assertThat(response.tierId()).isEqualTo(silver.getId());
        assertThat(response.tierCode()).isEqualTo("SILVER");
    }

    @Test
    void createRejectsUnknownTier() {
        UUID unknownTierId = UUID.randomUUID();
        when(tierRepository.findByIdAndIsDeletedFalse(unknownTierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.create(request("RULE-1", unknownTierId)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_TIER_NOT_FOUND);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(ruleRepository.existsByCodeAndIsDeletedFalse("RULE-1")).thenReturn(true);

        assertThatThrownBy(() -> ruleService.create(request("RULE-1", null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_POINT_RULE_CODE_EXISTS);
    }

    @Test
    void updateRejectsCodeUsedByAnotherRule() {
        LoyaltyPointRule rule = rule();
        when(ruleRepository.findByIdAndIsDeletedFalse(rule.getId())).thenReturn(Optional.of(rule));
        when(ruleRepository.existsByCodeAndIdNotAndIsDeletedFalse("RULE-2", rule.getId())).thenReturn(true);

        assertThatThrownBy(() -> ruleService.update(rule.getId(), request("RULE-2", null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_POINT_RULE_CODE_EXISTS);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void findApplicableRuleDefaultsNullAmountToZeroAndReturnsFirstMatch() {
        LoyaltyPointRule first = rule();
        when(ruleRepository.findApplicableRules(isNull(), eq(BigDecimal.ZERO), any(Instant.class),
                eq(LoyaltyPointRuleStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(first, rule()));

        Optional<LoyaltyPointRule> result = ruleService.findApplicableRule(null, null, null);

        assertThat(result).containsSame(first);
    }

    @Test
    void findApplicableRuleReturnsEmptyWhenNoRuleMatches() {
        when(ruleRepository.findApplicableRules(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThat(ruleService.findApplicableRule(UUID.randomUUID(), BigDecimal.TEN, EFFECTIVE_FROM)).isEmpty();
    }

    @Test
    void deleteSoftDeletesRule() {
        LoyaltyPointRule rule = rule();
        when(ruleRepository.findByIdAndIsDeletedFalse(rule.getId())).thenReturn(Optional.of(rule));

        ruleService.delete(rule.getId());

        assertThat(rule.getIsDeleted()).isTrue();
        verify(ruleRepository).save(rule);
    }

    @Test
    void getAllReturnsNonDeletedRules() {
        when(ruleRepository.findAllByIsDeletedFalse()).thenReturn(List.of(rule()));

        assertThat(ruleService.getAll()).extracting(LoyaltyPointRuleResponse::code).containsExactly("RULE-1");
    }

    @Test
    void getByIdReturnsRule() {
        LoyaltyPointRule rule = rule();
        when(ruleRepository.findByIdAndIsDeletedFalse(rule.getId())).thenReturn(Optional.of(rule));

        LoyaltyPointRuleResponse response = ruleService.getById(rule.getId());

        assertThat(response.id()).isEqualTo(rule.getId());
        assertThat(response.pointsPerBlock()).isEqualTo(1);
    }

    @Test
    void updateOverwritesRuleFields() {
        LoyaltyPointRule rule = rule();
        when(ruleRepository.findByIdAndIsDeletedFalse(rule.getId())).thenReturn(Optional.of(rule));
        LoyaltyPointRuleRequest request = new LoyaltyPointRuleRequest("RULE-1", "Tích điểm x2 cuối tuần", null, null,
                BigDecimal.valueOf(20_000), 2, BigDecimal.valueOf(100_000), false, false, new BigDecimal("2.00"), 10,
                EFFECTIVE_FROM, null, LoyaltyPointRuleStatus.ACTIVE);

        LoyaltyPointRuleResponse response = ruleService.update(rule.getId(), request);

        assertThat(response.name()).isEqualTo("Tích điểm x2 cuối tuần");
        assertThat(response.pointMultiplier()).isEqualByComparingTo("2");
        assertThat(response.earnAfterDiscount()).isFalse();
        assertThat(response.priority()).isEqualTo(10);
        verify(ruleRepository).save(rule);
    }

    @Test
    void getByIdRejectsMissingRule() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.getById(id))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.LOYALTY_POINT_RULE_NOT_FOUND);
    }

    private LoyaltyPointRuleRequest request(String code, UUID tierId) {
        return new LoyaltyPointRuleRequest(code, "Tích điểm cơ bản", null, tierId,
                BigDecimal.valueOf(10_000), 1, null, null, null, null, null,
                EFFECTIVE_FROM, null, LoyaltyPointRuleStatus.ACTIVE);
    }

    private LoyaltyPointRule rule() {
        LoyaltyPointRule rule = new LoyaltyPointRule();
        rule.setId(UUID.randomUUID());
        rule.setCode("RULE-1");
        rule.setName("Tích điểm cơ bản");
        rule.setSpendAmountPerBlock(BigDecimal.valueOf(10_000));
        rule.setPointsPerBlock(1);
        rule.setEffectiveFrom(EFFECTIVE_FROM);
        return rule;
    }
}
