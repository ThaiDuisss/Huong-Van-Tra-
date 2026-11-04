package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.ChangeTierStatusRequest;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerTierCountResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerTierStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.impl.CustomerTierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerTierServiceTest {

    private CustomerTierRepository tierRepository;
    private CustomerRepository customerRepository;
    private CustomerTierAssignmentRepository assignmentRepository;
    private CustomerTierServiceImpl tierService;

    @BeforeEach
    void setUp() {
        tierRepository = mock(CustomerTierRepository.class);
        customerRepository = mock(CustomerRepository.class);
        assignmentRepository = mock(CustomerTierAssignmentRepository.class);
        tierService = new CustomerTierServiceImpl(tierRepository, customerRepository, assignmentRepository, new CustomerMapper());
        when(tierRepository.save(any(CustomerTier.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getCountsWrapsActiveTiersWithAllAndUnassignedBuckets() {
        CustomerTier gold = tier("GOLD", 5_000_000, 10);
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findAllByStatusAndIsDeletedFalseOrderByMinSpendingDesc(CustomerTierStatus.ACTIVE))
                .thenReturn(List.of(gold, silver));
        when(customerRepository.countByTier_IdAndIsDeletedFalse(gold.getId())).thenReturn(2L);
        when(customerRepository.countByTier_IdAndIsDeletedFalse(silver.getId())).thenReturn(5L);
        when(customerRepository.countByIsDeletedFalse()).thenReturn(10L);
        when(customerRepository.countByTierIsNullAndIsDeletedFalse()).thenReturn(3L);

        List<CustomerTierCountResponse> counts = tierService.getCounts();

        assertThat(counts)
                .extracting(CustomerTierCountResponse::code, CustomerTierCountResponse::customerCount)
                .containsExactly(
                        tuple("ALL", 10L),
                        tuple("GOLD", 2L),
                        tuple("SILVER", 5L),
                        tuple("UNASSIGNED", 3L)
                );
    }

    @Test
    void createSavesTierWhenCodeIsAvailable() {
        when(tierRepository.existsByCode("SILVER")).thenReturn(false);

        CustomerTierResponse response = tierService.create(request("SILVER", 1_000_000, null));

        assertThat(response.code()).isEqualTo("SILVER");
        assertThat(response.minSpending()).isEqualByComparingTo("1000000");
        assertThat(response.minOrderCount()).isZero();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(tierRepository.existsByCode("SILVER")).thenReturn(true);

        assertThatThrownBy(() -> tierService.create(request("SILVER", 1_000_000, 3)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_TIER_CODE_EXISTS)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(tierRepository, never()).save(any());
    }

    @Test
    void updateRejectsThresholdChangeWhenTierHasAssignments() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));
        when(assignmentRepository.existsByTierIdAndIsDeletedFalse(silver.getId())).thenReturn(true);

        assertThatThrownBy(() -> tierService.update(silver.getId(), request("SILVER", 2_000_000, 3)))
                .isInstanceOf(CustomerServiceException.class)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(tierRepository, never()).save(any());
    }

    @Test
    void updateAllowsRenamingTierWithAssignmentsWhenThresholdsAreUnchanged() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));
        when(assignmentRepository.existsByTierIdAndIsDeletedFalse(silver.getId())).thenReturn(true);

        CustomerTierRequest request = new CustomerTierRequest(
                "SILVER", "Bạc", new BigDecimal("1000000.00"), 3, CustomerTierStatus.ACTIVE, "#C0C0C0");
        CustomerTierResponse response = tierService.update(silver.getId(), request);

        assertThat(response.name()).isEqualTo("Bạc");
        assertThat(response.colorCode()).isEqualTo("#C0C0C0");
    }

    @Test
    void updateAllowsThresholdChangeWhenTierHasNoAssignments() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));
        when(assignmentRepository.existsByTierIdAndIsDeletedFalse(silver.getId())).thenReturn(false);

        CustomerTierResponse response = tierService.update(silver.getId(), request("SILVER", 2_000_000, 5));

        assertThat(response.minSpending()).isEqualByComparingTo("2000000");
        assertThat(response.minOrderCount()).isEqualTo(5);
    }

    @Test
    void updateRejectsCodeUsedByAnotherTier() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));
        when(tierRepository.existsByCodeAndIdNot("GOLD", silver.getId())).thenReturn(true);

        assertThatThrownBy(() -> tierService.update(silver.getId(), request("GOLD", 1_000_000, 3)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_TIER_CODE_EXISTS);
    }

    @Test
    void changeTierStatusUpdatesOnlyStatus() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));

        tierService.changeTierStatus(silver.getId(), new ChangeTierStatusRequest(CustomerTierStatus.INACTIVE));

        assertThat(silver.getStatus()).isEqualTo(CustomerTierStatus.INACTIVE);
        assertThat(silver.getMinSpending()).isEqualByComparingTo("1000000");
        verify(tierRepository).save(silver);
    }

    @Test
    void getAllReturnsNonDeletedTiers() {
        when(tierRepository.findAllByIsDeletedFalse()).thenReturn(List.of(tier("MEMBER", 0, 0), tier("SILVER", 1_000_000, 3)));

        assertThat(tierService.getAll()).extracting(CustomerTierResponse::code).containsExactly("MEMBER", "SILVER");
    }

    @Test
    void getAllReturnsEmptyListWhenNoTierConfigured() {
        when(tierRepository.findAllByIsDeletedFalse()).thenReturn(List.of());

        assertThat(tierService.getAll()).isEmpty();
    }

    @Test
    void getByIdReturnsTier() {
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findByIdAndIsDeletedFalse(silver.getId())).thenReturn(Optional.of(silver));

        CustomerTierResponse response = tierService.getById(silver.getId());

        assertThat(response.id()).isEqualTo(silver.getId());
        assertThat(response.minOrderCount()).isEqualTo(3);
    }

    @Test
    void getByIdRejectsMissingTier() {
        UUID id = UUID.randomUUID();
        when(tierRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tierService.getById(id))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_TIER_NOT_FOUND);
    }

    private CustomerTierRequest request(String code, int minSpending, Integer minOrderCount) {
        return new CustomerTierRequest(code, code, BigDecimal.valueOf(minSpending), minOrderCount,
                CustomerTierStatus.ACTIVE, "#000000");
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
