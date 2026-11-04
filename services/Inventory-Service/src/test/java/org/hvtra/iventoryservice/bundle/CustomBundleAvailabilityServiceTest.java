package org.hvtra.iventoryservice.bundle;

import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleLineRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse.MaterialLine;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService.Evaluation;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CustomBundleAvailabilityService Unit Tests")
class CustomBundleAvailabilityServiceTest {

    private static final UUID BRANCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    private static final UUID WAREHOUSE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");
    private static final UUID SHAN_INGREDIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440800");
    private static final UUID LOTUS_INGREDIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440801");
    private static final UUID SHAN_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440810");
    private static final UUID LOTUS_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440811");

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private AvailabilityEstimateService estimateService;

    private CustomBundleAvailabilityService service;
    private InventoryLocation warehouse;
    private InventoryItem shanTuyet;
    private InventoryItem lotusPetal;

    @BeforeEach
    void setUp() {
        service = new CustomBundleAvailabilityService(itemRepository, locationRepository, balanceRepository, estimateService);
        warehouse = new InventoryLocation();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setBranchId(BRANCH_ID);
        warehouse.setType(InventoryLocationType.WAREHOUSE);
        shanTuyet = item(SHAN_ITEM_ID, SHAN_INGREDIENT_ID, "NL-00001", "Trà Shan Tuyết");
        lotusPetal = item(LOTUS_ITEM_ID, LOTUS_INGREDIENT_ID, "NL-00002", "Cánh sen sấy");

        when(itemRepository.findByReferenceIdInAndItemType(anyCollection(), eq(InventoryItemType.INGREDIENT)))
                .thenReturn(List.of(shanTuyet, lotusPetal));
        when(locationRepository.findFirstByBranchIdAndTypeAndStatus(
                BRANCH_ID, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)).thenReturn(Optional.of(warehouse));
        List<Object[]> available = new ArrayList<>();
        available.add(new Object[]{SHAN_ITEM_ID, new BigDecimal("1.00")});
        when(balanceRepository.sumFefoAvailableByItemsAtLocation(anyCollection(), eq(WAREHOUSE_ID), eq(InventoryStatus.ACTIVE), any()))
                .thenReturn(available);
    }

    @Nested
    @DisplayName("check")
    class CheckTests {

        @Test
        @DisplayName("check merges duplicate ingredients and reports enough when nothing is missing")
        void check_WhenAllAvailable_ShouldReportEnough() {
            stubEstimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", "1.00", null);
            stubEstimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0", null);

            CustomBundleAvailabilityResponse response = service.check(BRANCH_ID, List.of(
                    new CustomBundleLineRequest(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", new BigDecimal("0.03")),
                    new CustomBundleLineRequest(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", new BigDecimal("0.02")),
                    new CustomBundleLineRequest(LOTUS_INGREDIENT_ID, "Cánh sen sấy", new BigDecimal("0.03"))));

            assertThat(response.enough()).isTrue();
            assertThat(response.estimatedAvailableAt()).isNull();
            assertThat(response.estimatedWaitDays()).isZero();
            assertThat(response.lines()).extracting(MaterialLine::ingredientCode).containsExactly("NL-00001", "NL-00002");
            verify(estimateService).estimateMaterialArrival(SHAN_INGREDIENT_ID, "Trà Shan Tuyết",
                    new BigDecimal("0.05"), new BigDecimal("1.00"), shanTuyet);
            verify(estimateService).estimateMaterialArrival(LOTUS_INGREDIENT_ID, "Cánh sen sấy",
                    new BigDecimal("0.03"), BigDecimal.ZERO, lotusPetal);
        }

        @Test
        @DisplayName("check reports the latest arrival and rounds waiting days up")
        void check_WhenMaterialsMissing_ShouldReportLatestEta() {
            Instant soon = Instant.now().plus(Duration.ofHours(20));
            Instant later = Instant.now().plus(Duration.ofDays(2)).plus(Duration.ofHours(1));
            stubEstimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", "0.01", soon);
            stubEstimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0", later);

            CustomBundleAvailabilityResponse response = service.check(BRANCH_ID, lines());

            assertThat(response.enough()).isFalse();
            assertThat(response.estimatedAvailableAt()).isEqualTo(later);
            assertThat(response.estimatedWaitDays()).isEqualTo(3);
            assertThat(response.lines().get(0).missingQuantity()).isEqualByComparingTo("0.04");
        }

        @Test
        @DisplayName("check with an overdue arrival still reports missing but zero waiting days")
        void check_WhenEtaAlreadyPassed_ShouldReportZeroWaitDays() {
            Instant overdue = Instant.now().minus(Duration.ofHours(3));
            stubEstimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", "0.01", overdue);
            stubEstimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0.03", null);

            CustomBundleAvailabilityResponse response = service.check(BRANCH_ID, lines());

            assertThat(response.enough()).isFalse();
            assertThat(response.estimatedWaitDays()).isZero();
        }

        @Test
        @DisplayName("check uses the first active warehouse when no branch is given")
        void check_WhenBranchMissing_ShouldUseDefaultWarehouse() {
            when(locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                    .thenReturn(Optional.of(warehouse));
            stubEstimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", "1.00", null);
            stubEstimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0.03", null);

            assertThat(service.check(null, lines()).enough()).isTrue();
            verify(locationRepository, never()).findFirstByBranchIdAndTypeAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("check with an ingredient not stocked throws BadRequestException")
        void check_WhenIngredientNotStocked_ShouldThrow() {
            when(itemRepository.findByReferenceIdInAndItemType(anyCollection(), eq(InventoryItemType.INGREDIENT)))
                    .thenReturn(List.of(shanTuyet));

            assertThatThrownBy(() -> service.check(BRANCH_ID, lines()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nguyên liệu Cánh sen sấy chưa có trong kho");
        }

        @Test
        @DisplayName("check with an unnamed ingredient not stocked throws a generic BadRequestException")
        void check_WhenUnnamedIngredientNotStocked_ShouldThrowGenericMessage() {
            when(itemRepository.findByReferenceIdInAndItemType(anyCollection(), eq(InventoryItemType.INGREDIENT)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.check(BRANCH_ID, List.of(
                    new CustomBundleLineRequest(LOTUS_INGREDIENT_ID, null, new BigDecimal("0.03")))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Có nguyên liệu đã chọn chưa có trong kho");
        }

        @Test
        @DisplayName("check with an inactive ingredient throws BadRequestException")
        void check_WhenIngredientInactive_ShouldThrow() {
            lotusPetal.setStatus(InventoryStatus.INACTIVE);

            assertThatThrownBy(() -> service.check(BRANCH_ID, lines()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nguyên liệu Cánh sen sấy đã ngừng hoạt động trong kho");
        }

        @Test
        @DisplayName("check without an active warehouse in the branch throws BadRequestException")
        void check_WhenBranchHasNoWarehouse_ShouldThrow() {
            when(locationRepository.findFirstByBranchIdAndTypeAndStatus(
                    BRANCH_ID, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.check(BRANCH_ID, lines()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No active WAREHOUSE location found for branch " + BRANCH_ID);
        }
    }

    @Nested
    @DisplayName("evaluate")
    class EvaluateTests {

        @Test
        @DisplayName("evaluate returns the reservation warehouse and inventory item per ingredient")
        void evaluate_WhenAvailable_ShouldExposeLocationAndMaterials() {
            stubEstimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", "1.00", null);
            stubEstimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0.03", null);

            Evaluation evaluation = service.evaluate(BRANCH_ID, lines());

            assertThat(evaluation.enough()).isTrue();
            assertThat(evaluation.location()).isSameAs(warehouse);
            assertThat(evaluation.materials()).extracting(CustomBundleAvailabilityService.Material::item)
                    .containsExactly(shanTuyet, lotusPetal);
        }

        @Test
        @DisplayName("evaluate with no active warehouse at all throws BadRequestException")
        void evaluate_WhenNoWarehouseAnywhere_ShouldThrow() {
            when(locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.evaluate(null, lines()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("No active WAREHOUSE location found");
        }
    }

    private List<CustomBundleLineRequest> lines() {
        return List.of(
                new CustomBundleLineRequest(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", new BigDecimal("0.05")),
                new CustomBundleLineRequest(LOTUS_INGREDIENT_ID, "Cánh sen sấy", new BigDecimal("0.03")));
    }

    private void stubEstimate(UUID ingredientId, String name, String required, String available, Instant eta) {
        BigDecimal requiredQuantity = new BigDecimal(required);
        BigDecimal availableQuantity = new BigDecimal(available);
        BigDecimal missing = requiredQuantity.subtract(availableQuantity).max(BigDecimal.ZERO);
        when(estimateService.estimateMaterialArrival(eq(ingredientId), any(), any(), any(), any()))
                .thenReturn(new MaterialEstimate(ingredientId, name, requiredQuantity, availableQuantity, missing,
                        eta, eta == null ? null : "OPEN_RECEIPT", eta == null ? null : "PN-00012"));
    }

    private InventoryItem item(UUID id, UUID ingredientId, String code, String name) {
        InventoryItem value = new InventoryItem();
        value.setId(id);
        value.setReferenceId(ingredientId);
        value.setReferenceCode(code);
        value.setName(name);
        value.setItemType(InventoryItemType.INGREDIENT);
        value.setStatus(InventoryStatus.ACTIVE);
        return value;
    }
}
