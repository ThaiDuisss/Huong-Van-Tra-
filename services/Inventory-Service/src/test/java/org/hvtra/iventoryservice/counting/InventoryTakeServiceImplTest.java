package org.hvtra.iventoryservice.counting;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.ReturnForRecountRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest.CountLineUpdate;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeLineResponse;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.counting.mapper.InventoryTakeMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryCountRepository;
import org.hvtra.iventoryservice.counting.service.InventoryAdjustmentService;
import org.hvtra.iventoryservice.counting.service.InventoryTakeServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountAssignment;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountAttempt;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentRole;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient.VariantResponse;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionLineRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryTakeServiceImpl Unit Tests")
class InventoryTakeServiceImplTest {

    private static final UUID COUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID BRANCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    private static final UUID WAREHOUSE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");
    private static final UUID COUNTER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    private static final UUID MANAGER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");
    private static final UUID SECOND_COUNTER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440022");
    private static final UUID ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
    private static final UUID VARIANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
    private static final UUID LOCATION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440200");
    private static final UUID OTHER_LOCATION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
    private static final UUID BATCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
    private static final UUID LINE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440400");
    private static final UUID SECOND_LINE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440401");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Mock
    private InventoryCountRepository countRepository;
    @Mock
    private InventoryBalanceRepository balanceRepository;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventoryTransactionLineRepository transactionLineRepository;
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private InventoryAdjustmentService adjustmentService;

    private InventoryTakeServiceImpl service;
    private InventoryItem item;
    private InventoryLocation location;
    private InventoryLocation otherLocation;
    private InventoryBatch batch;

    @BeforeEach
    void setUp() {
        service = new InventoryTakeServiceImpl(countRepository, balanceRepository, locationRepository, itemRepository,
                new InventoryTakeMapper(), transactionLineRepository, catalogClient, adjustmentService);

        item = new InventoryItem();
        item.setId(ITEM_ID);
        item.setReferenceId(VARIANT_ID);
        item.setName("Trà Shan Tuyết hộp 100g");
        item.setStatus(InventoryStatus.ACTIVE);
        location = location(LOCATION_ID, BRANCH_ID);
        otherLocation = location(OTHER_LOCATION_ID, BRANCH_ID);
        batch = new InventoryBatch();
        batch.setId(BATCH_ID);
        batch.setBatchCode("LOT-20261103-001");
        batch.setInventoryItem(item);

        when(countRepository.save(any(InventoryCount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionLineRepository.sumMovementAfterSnapshot(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionLineRepository.sumBatchMovementAfterSnapshot(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        authenticate(MANAGER_ID.toString(), "MANAGER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("findAll / findById")
    class QueryTests {

        @Test
        @DisplayName("findAll maps matching counts to a page")
        void findAll_WhenCountsMatch_ShouldReturnPage() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(countRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(count(InventoryCountStatus.DRAFT)), pageable, 1));

            PageResponse<InventoryTakeResponse> page = service.findAll(
                    BRANCH_ID, List.of(InventoryCountStatus.DRAFT), InventoryCountScopeType.FULL, "CT-2026", pageable);

            assertThat(page.content()).singleElement()
                    .extracting(InventoryTakeResponse::countCode)
                    .isEqualTo("CT-20261103-1A2B3C4D");
            assertThat(page.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("findAll returns an empty page when nothing matches")
        void findAll_WhenNothingMatches_ShouldReturnEmptyPage() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(countRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            PageResponse<InventoryTakeResponse> page = service.findAll(null, null, null, null, pageable);

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
        }

        @Test
        @DisplayName("findById returns the count with its lines")
        void findById_WhenCountExists_ShouldReturnResponse() {
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            InventoryTakeResponse response = service.findById(COUNT_ID);

            assertThat(response.id()).isEqualTo(COUNT_ID);
            assertThat(response.lines()).singleElement()
                    .extracting(InventoryTakeLineResponse::expectedQuantity)
                    .isEqualTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("findById hides expected quantities of a blind count from inventory staff")
        void findById_WhenBlindCountViewedByStaff_ShouldHideExpectedQuantity() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.setBlindCount(true);
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            InventoryTakeLineResponse line = service.findById(COUNT_ID).lines().get(0);

            assertThat(line.snapshotQuantity()).isNull();
            assertThat(line.expectedQuantity()).isNull();
            assertThat(line.snapshotUnitCost()).isNull();
        }

        @Test
        @DisplayName("findById throws ResourceNotFoundException for unknown id")
        void findById_WhenCountMissing_ShouldThrowNotFound() {
            when(countRepository.findByIdAndIsDeletedFalse(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(UNKNOWN_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Inventory count not found: " + UNKNOWN_ID);
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("create FULL scope generates code, infers FULL_COUNT and dedupes counters")
        void create_WhenFullScope_ShouldCreateDraftWithAssignments() {
            InventoryTakeResponse response = service.create(new CreateInventoryTakeRequest(
                    BRANCH_ID, WAREHOUSE_ID, null, InventoryCountScopeType.FULL, null, null, null,
                    List.of(COUNTER_ID, COUNTER_ID), MANAGER_ID, true, "Kiểm kê cuối tháng 11"));

            assertThat(response.countCode()).matches("^CT-\\d{8}-[0-9A-F]{8}$");
            assertThat(response.status()).isEqualTo(InventoryCountStatus.DRAFT);
            assertThat(response.countType()).isEqualTo(InventoryCountType.FULL_COUNT);
            assertThat(response.blindCount()).isTrue();
            assertThat(response.assignments())
                    .extracting(InventoryTakeResponse.AssignmentSummary::userId, InventoryTakeResponse.AssignmentSummary::role)
                    .containsExactly(
                            tuple(COUNTER_ID, InventoryCountAssignmentRole.COUNTER),
                            tuple(MANAGER_ID, InventoryCountAssignmentRole.SUPERVISOR));
        }

        @Test
        @DisplayName("create LOCATION scope with an active location of the branch infers SPOT_COUNT")
        void create_WhenLocationScopeValid_ShouldInferSpotCount() {
            when(locationRepository.findAllById(List.of(LOCATION_ID))).thenReturn(List.of(location));

            InventoryTakeResponse response = service.create(request(InventoryCountScopeType.LOCATION,
                    List.of(LOCATION_ID), null, null, null));

            assertThat(response.countType()).isEqualTo(InventoryCountType.SPOT_COUNT);
            assertThat(response.locationIds()).containsExactly(LOCATION_ID);
        }

        @Test
        @DisplayName("create keeps an explicitly requested count type")
        void create_WhenCountTypeGiven_ShouldKeepIt() {
            InventoryTakeResponse response = service.create(new CreateInventoryTakeRequest(
                    BRANCH_ID, null, InventoryCountType.CYCLE_COUNT, InventoryCountScopeType.FULL,
                    null, null, null, null, null, null, null));

            assertThat(response.countType()).isEqualTo(InventoryCountType.CYCLE_COUNT);
            assertThat(response.blindCount()).isFalse();
            assertThat(response.assignments()).isEmpty();
        }

        @Test
        @DisplayName("create LOCATION scope without locationIds throws BadRequestException")
        void create_WhenLocationIdsMissing_ShouldThrow() {
            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.LOCATION, List.of(), null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("locationIds bắt buộc khi scopeType = LOCATION");
            verify(countRepository, never()).save(any());
        }

        @Test
        @DisplayName("create LOCATION scope with unknown location throws BadRequestException")
        void create_WhenLocationUnknown_ShouldThrow() {
            when(locationRepository.findAllById(List.of(UNKNOWN_ID))).thenReturn(List.of());

            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.LOCATION, List.of(UNKNOWN_ID), null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Một hoặc nhiều vị trí kiểm kê không tồn tại");
        }

        @Test
        @DisplayName("create LOCATION scope with a location of another branch throws BadRequestException")
        void create_WhenLocationInOtherBranch_ShouldThrow() {
            InventoryLocation foreign = location(OTHER_LOCATION_ID, OTHER_BRANCH_ID);
            when(locationRepository.findAllById(List.of(OTHER_LOCATION_ID))).thenReturn(List.of(foreign));

            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.LOCATION, List.of(OTHER_LOCATION_ID), null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Vị trí kiểm kê phải hoạt động và thuộc chi nhánh đã chọn");
        }

        @Test
        @DisplayName("create PARTIAL scope without items throws BadRequestException")
        void create_WhenItemIdsMissing_ShouldThrow() {
            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.PARTIAL, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("inventoryItemIds bắt buộc khi scopeType = PARTIAL");
        }

        @Test
        @DisplayName("create ITEM scope with inactive item throws BadRequestException")
        void create_WhenItemInactive_ShouldThrow() {
            item.setStatus(InventoryStatus.INACTIVE);
            when(itemRepository.findAllById(List.of(ITEM_ID))).thenReturn(List.of(item));

            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.ITEM, null, List.of(ITEM_ID), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Mặt hàng kiểm kê phải ở trạng thái hoạt động");
        }

        @Test
        @DisplayName("create ITEM scope with unknown item throws BadRequestException")
        void create_WhenItemUnknown_ShouldThrow() {
            when(itemRepository.findAllById(List.of(UNKNOWN_ID))).thenReturn(List.of());

            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.ITEM, null, List.of(UNKNOWN_ID), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Một hoặc nhiều mặt hàng kiểm kê không tồn tại");
        }

        @Test
        @DisplayName("create BATCH scope without batches throws BadRequestException")
        void create_WhenBatchIdsMissing_ShouldThrow() {
            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.BATCH, null, null, List.of(), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("batchIds bắt buộc khi scopeType = BATCH");
        }

        @Test
        @DisplayName("create by sale staff throws AccessDeniedException")
        void create_WhenSaleStaff_ShouldDeny() {
            authenticate(COUNTER_ID.toString(), "SALE_STAFF");

            assertThatThrownBy(() -> service.create(request(InventoryCountScopeType.FULL, null, null, null, null)))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền thực hiện thao tác kiểm kê này");
        }
    }

    @Nested
    @DisplayName("start")
    class StartTests {

        @Test
        @DisplayName("start locks the scope, snapshots non-batch balances and unit cost")
        void start_WhenBalancesExist_ShouldCreateLinesAndStartCounting() {
            stubCount(count(InventoryCountStatus.DRAFT));
            when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance(null, "10.00")));
            when(balanceRepository.findByBranchWithPositiveBalance(BRANCH_ID)).thenReturn(List.of());
            when(catalogClient.getVariantsBatch(List.of(VARIANT_ID))).thenReturn(List.of(variant("50000")));

            InventoryTakeResponse response = service.start(COUNT_ID);

            assertThat(response.status()).isEqualTo(InventoryCountStatus.COUNTING);
            assertThat(response.startedAt()).isNotNull();
            assertThat(response.lines()).singleElement().satisfies(line -> {
                assertThat(line.snapshotQuantity()).isEqualByComparingTo("10.00");
                assertThat(line.snapshotUnitCost()).isEqualByComparingTo("50000");
                assertThat(line.inventoryBatchId()).isNull();
                assertThat(line.counted()).isFalse();
            });
        }

        @Test
        @DisplayName("start creates batch lines instead of the item total when batch balances exist")
        void start_WhenBatchBalancesExist_ShouldCreateBatchLinesOnly() {
            stubCount(count(InventoryCountStatus.DRAFT));
            when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance(null, "10.00")));
            when(balanceRepository.findByBranchWithPositiveBalance(BRANCH_ID)).thenReturn(List.of(balance(batch, "6.00")));
            when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of());

            InventoryTakeResponse response = service.start(COUNT_ID);

            assertThat(response.lines()).singleElement().satisfies(line -> {
                assertThat(line.batchCode()).isEqualTo("LOT-20261103-001");
                assertThat(line.snapshotQuantity()).isEqualByComparingTo("6.00");
                assertThat(line.snapshotUnitCost()).isEqualByComparingTo("0");
            });
        }

        @Test
        @DisplayName("start without any stock in scope throws BadRequestException")
        void start_WhenNoBalance_ShouldThrow() {
            stubCount(count(InventoryCountStatus.DRAFT));
            when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of());
            when(balanceRepository.findByBranchWithPositiveBalance(BRANCH_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.start(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không tìm thấy tồn kho để kiểm kê");
        }

        @Test
        @DisplayName("start rejects a scope overlapping a count that is already counting")
        void start_WhenOpenFullCountExists_ShouldThrow() {
            stubCount(count(InventoryCountStatus.DRAFT));
            InventoryCount open = count(InventoryCountStatus.COUNTING);
            open.setId(UUID.randomUUID());
            when(countRepository.findByBranchIdAndStatusInAndIsDeletedFalse(eq(BRANCH_ID), any())).thenReturn(List.of(open));

            assertThatThrownBy(() -> service.start(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Đã có kỳ kiểm kê đang mở trùng phạm vi");
        }

        @Test
        @DisplayName("start only checks overlap against COUNTING and PENDING_APPROVAL counts")
        void start_ShouldIgnoreDraftCountsWhenCheckingOverlap() {
            stubCount(count(InventoryCountStatus.DRAFT));
            when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance(null, "10.00")));
            when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of());

            service.start(COUNT_ID);

            verify(countRepository).findByBranchIdAndStatusInAndIsDeletedFalse(BRANCH_ID,
                    EnumSet.of(InventoryCountStatus.COUNTING, InventoryCountStatus.PENDING_APPROVAL));
        }

        @Test
        @DisplayName("start ignores open counts of a different warehouse")
        void start_WhenOpenCountInOtherWarehouse_ShouldStart() {
            InventoryCount candidate = count(InventoryCountStatus.DRAFT);
            candidate.setWarehouseId(WAREHOUSE_ID);
            stubCount(candidate);
            InventoryCount open = count(InventoryCountStatus.COUNTING);
            open.setId(UUID.randomUUID());
            open.setWarehouseId(UUID.randomUUID());
            when(countRepository.findByBranchIdAndStatusInAndIsDeletedFalse(eq(BRANCH_ID), any())).thenReturn(List.of(open));
            when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance(null, "10.00")));
            when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of());

            assertThat(service.start(COUNT_ID).status()).isEqualTo(InventoryCountStatus.COUNTING);
        }

        @Test
        @DisplayName("start outside DRAFT throws BadRequestException")
        void start_WhenNotDraft_ShouldThrow() {
            stubCount(count(InventoryCountStatus.COUNTING));

            assertThatThrownBy(() -> service.start(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ có thể bắt đầu kiểm kê khi kỳ kiểm kê ở trạng thái DRAFT");
        }

        @Test
        @DisplayName("start by inventory staff throws AccessDeniedException")
        void start_WhenInventoryStaff_ShouldDeny() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");

            assertThatThrownBy(() -> service.start(COUNT_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("updateCounts")
    class UpdateCountsTests {

        @Test
        @DisplayName("updateCounts records quantity, variance, attempt and marks the counter done")
        void updateCounts_WhenAssignedCounter_ShouldRecordCount() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            InventoryCountAssignment counter = assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            count.addAssignment(counter);
            stubCount(count);

            InventoryTakeResponse response = service.updateCounts(COUNT_ID, counts(update(LINE_ID, "9.00", null)));

            InventoryTakeLineResponse line = response.lines().get(0);
            assertThat(line.counted()).isTrue();
            assertThat(line.countedQuantity()).isEqualByComparingTo("9.00");
            assertThat(line.varianceQuantity()).isEqualByComparingTo("-1.00");
            assertThat(line.goodQuantity()).isEqualByComparingTo("9.00");
            assertThat(line.attempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.attemptNo()).isEqualTo(1);
                assertThat(attempt.counterId()).isEqualTo(COUNTER_ID);
            });
            assertThat(counter.getStatus()).isEqualTo(InventoryCountAssignmentStatus.DONE);
            assertThat(counter.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("updateCounts keeps the counter assigned while lines remain uncounted")
        void updateCounts_WhenLinesRemain_ShouldKeepAssigned() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            count.addLine(line(SECOND_LINE_ID, location, "4.00"));
            InventoryCountAssignment counter = assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            count.addAssignment(counter);
            stubCount(count);

            service.updateCounts(COUNT_ID, counts(update(LINE_ID, "10.00", null)));

            assertThat(counter.getStatus()).isEqualTo(InventoryCountAssignmentStatus.ASSIGNED);
        }

        @Test
        @DisplayName("updateCounts accepts zero for legacy counts without counter assignments")
        void updateCounts_WhenLegacyCountAndZero_ShouldRecordFullShortage() {
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            InventoryTakeResponse response = service.updateCounts(COUNT_ID, counts(update(LINE_ID, "0", null)));

            assertThat(response.lines().get(0).varianceQuantity()).isEqualByComparingTo("-10.00");
        }

        @Test
        @DisplayName("updateCounts by a user not assigned to the count throws BadRequestException")
        void updateCounts_WhenNotAssigned_ShouldThrow() {
            authenticate(SECOND_COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            count.addAssignment(assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER));
            stubCount(count);

            assertThatThrownBy(() -> service.updateCounts(COUNT_ID, counts(update(LINE_ID, "9.00", null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bạn không được phân công kiểm kê kỳ này");
        }

        @Test
        @DisplayName("updateCounts on a line outside the assigned location throws BadRequestException")
        void updateCounts_WhenLineOutsideAssignedLocation_ShouldThrow() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, otherLocation, "10.00"));
            InventoryCountAssignment counter = assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            counter.setLocation(location);
            count.addAssignment(counter);
            stubCount(count);

            assertThatThrownBy(() -> service.updateCounts(COUNT_ID, counts(update(LINE_ID, "9.00", null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Có dòng không thuộc vị trí được phân công");
        }

        @Test
        @DisplayName("updateCounts outside COUNTING throws BadRequestException")
        void updateCounts_WhenNotCounting_ShouldThrow() {
            stubCount(count(InventoryCountStatus.PENDING_APPROVAL));

            assertThatThrownBy(() -> service.updateCounts(COUNT_ID, counts(update(LINE_ID, "9.00", null))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ có thể nhập số đếm khi đang COUNTING");
        }

        @Test
        @DisplayName("updateCounts with unknown line throws ResourceNotFoundException")
        void updateCounts_WhenLineUnknown_ShouldThrow() {
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            assertThatThrownBy(() -> service.updateCounts(COUNT_ID, counts(update(UNKNOWN_ID, "9.00", null))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Inventory count line not found: " + UNKNOWN_ID);
        }

        @Test
        @DisplayName("updateCounts with condition total different from counted quantity throws BadRequestException")
        void updateCounts_WhenConditionTotalMismatch_ShouldThrow() {
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            assertThatThrownBy(() -> service.updateCounts(COUNT_ID, counts(update(LINE_ID, "9.00", "7.00"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Tổng số lượng theo tình trạng phải bằng số lượng thực đếm");
        }
    }

    @Nested
    @DisplayName("finish")
    class FinishTests {

        @Test
        @DisplayName("finish without variance completes the count without an adjustment")
        void finish_WhenNoVariance_ShouldComplete() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = countingCount("10.00");
            count.addAssignment(assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER));
            stubCount(count);

            InventoryTakeResponse response = service.finish(COUNT_ID);

            assertThat(response.status()).isEqualTo(InventoryCountStatus.COMPLETED);
            assertThat(response.completedBy()).isEqualTo(COUNTER_ID);
            assertThat(response.submittedAt()).isNotNull();
            assertThat(response.adjustmentDocument()).isNull();
            verify(adjustmentService, never()).createAndPostFromCount(any(), any());
        }

        @Test
        @DisplayName("finish with any variance waits for approval")
        void finish_WhenVariance_ShouldWaitForApproval() {
            stubCount(countingCount("9.00"));

            assertThat(service.finish(COUNT_ID).status()).isEqualTo(InventoryCountStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("finish nets stock movements after the snapshot out of the variance")
        void finish_WhenMovementAfterSnapshot_ShouldReconcileExpectedQuantity() {
            InventoryCount count = countingCount("13.00");
            stubCount(count);
            when(transactionLineRepository.sumMovementAfterSnapshot(eq(ITEM_ID), eq(LOCATION_ID), any()))
                    .thenReturn(new BigDecimal("3.00"));

            InventoryTakeResponse response = service.finish(COUNT_ID);

            assertThat(response.status()).isEqualTo(InventoryCountStatus.COMPLETED);
            assertThat(response.lines().get(0).expectedQuantity()).isEqualByComparingTo("13.00");
            assertThat(response.lines().get(0).varianceQuantity()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("finish uses batch movements for batch lines")
        void finish_WhenBatchLine_ShouldUseBatchMovement() {
            InventoryCount count = countingCount("10.00");
            count.getLines().get(0).setInventoryBatch(batch);
            stubCount(count);

            service.finish(COUNT_ID);

            verify(transactionLineRepository).sumBatchMovementAfterSnapshot(eq(BATCH_ID), eq(LOCATION_ID), any());
            verify(transactionLineRepository, never()).sumMovementAfterSnapshot(any(), any(), any());
        }

        @Test
        @DisplayName("finish with uncounted lines throws BadRequestException")
        void finish_WhenLineNotCounted_ShouldThrow() {
            InventoryCount count = count(InventoryCountStatus.COUNTING);
            count.setStartedAt(Instant.parse("2026-11-03T01:00:00Z"));
            count.addLine(line(LINE_ID, location, "10.00"));
            stubCount(count);

            assertThatThrownBy(() -> service.finish(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Tất cả dòng kiểm kê phải được đếm trước khi kết thúc");
        }

        @Test
        @DisplayName("finish without snapshot throws BadRequestException")
        void finish_WhenNoSnapshot_ShouldThrow() {
            stubCount(count(InventoryCountStatus.COUNTING));

            assertThatThrownBy(() -> service.finish(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Kỳ kiểm kê chưa có snapshot");
        }

        @Test
        @DisplayName("finish by a counter not assigned throws BadRequestException")
        void finish_WhenNotAssigned_ShouldThrow() {
            authenticate(SECOND_COUNTER_ID.toString(), "INVENTORY_STAFF");
            InventoryCount count = countingCount("10.00");
            count.addAssignment(assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER));
            stubCount(count);

            assertThatThrownBy(() -> service.finish(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bạn không được phân công kiểm kê kỳ này");
        }
    }

    @Nested
    @DisplayName("approve")
    class ApproveTests {

        @Test
        @DisplayName("approve posts the adjustment and completes the count")
        void approve_WhenPendingApproval_ShouldPostAdjustmentAndComplete() {
            InventoryCount count = pendingCount("9.00");
            stubCount(count);
            InventoryAdjustment adjustment = InventoryAdjustment.forNetQuantity(new BigDecimal("-1.00"));
            adjustment.setId(UUID.randomUUID());
            adjustment.setDocumentCode("ADJ-20261104-0001");
            when(adjustmentService.createAndPostFromCount(count, MANAGER_ID)).thenReturn(adjustment);

            InventoryTakeResponse response = service.approve(COUNT_ID);

            assertThat(response.status()).isEqualTo(InventoryCountStatus.COMPLETED);
            assertThat(response.completedBy()).isEqualTo(MANAGER_ID);
            assertThat(response.completedAt()).isNotNull();
            assertThat(response.adjustmentDocument().documentCode()).isEqualTo("ADJ-20261104-0001");
        }

        @Test
        @DisplayName("approve outside PENDING_APPROVAL throws and posts nothing")
        void approve_WhenCounting_ShouldThrow() {
            stubCount(countingCount("9.00"));

            assertThatThrownBy(() -> service.approve(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Kỳ kiểm kê không ở trạng thái chờ phê duyệt");
            verify(adjustmentService, never()).createAndPostFromCount(any(), any());
        }

        @Test
        @DisplayName("approve propagates a posting failure so the transaction rolls back")
        void approve_WhenPostingFails_ShouldThrow() {
            InventoryCount count = pendingCount("9.00");
            stubCount(count);
            when(adjustmentService.createAndPostFromCount(count, MANAGER_ID))
                    .thenThrow(new BadRequestException("Tồn kho không đủ để điều chỉnh giảm cho mặt hàng " + ITEM_ID));

            assertThatThrownBy(() -> service.approve(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageStartingWith("Tồn kho không đủ");
            verify(countRepository, never()).save(any());
        }

        @Test
        @DisplayName("approve by inventory staff throws AccessDeniedException")
        void approve_WhenInventoryStaff_ShouldDeny() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");

            assertThatThrownBy(() -> service.approve(COUNT_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền thực hiện thao tác kiểm kê này");
        }

        @Test
        @DisplayName("approve with a non-UUID user id throws BadRequestException")
        void approve_WhenUserIdNotUuid_ShouldThrow() {
            authenticate("nv-kho-01", "MANAGER");
            stubCount(pendingCount("9.00"));

            assertThatThrownBy(() -> service.approve(COUNT_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("User ID hiện tại không phải UUID hợp lệ");
        }
    }

    @Nested
    @DisplayName("returnForRecount")
    class ReturnForRecountTests {

        @Test
        @DisplayName("returnForRecount resets the lines, assigns a new counter and reopens counting")
        void returnForRecount_WhenPendingApproval_ShouldReopenCounting() {
            InventoryCount count = pendingCount("9.00");
            InventoryCountLine line = count.getLines().get(0);
            line.addAttempt(new InventoryCountAttempt());
            stubCount(count);

            InventoryTakeResponse response = service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(LINE_ID), SECOND_COUNTER_ID, "  Lệch 1 hộp, cần đếm lại  "));

            assertThat(response.status()).isEqualTo(InventoryCountStatus.COUNTING);
            assertThat(response.returnReason()).isEqualTo("Lệch 1 hộp, cần đếm lại");
            assertThat(line.getCountedQuantity()).isNull();
            assertThat(line.getAttempts()).hasSize(1);
            assertThat(response.assignments()).extracting(InventoryTakeResponse.AssignmentSummary::userId)
                    .contains(SECOND_COUNTER_ID);
        }

        @Test
        @DisplayName("returnForRecount reopens an already assigned counter instead of duplicating it")
        void returnForRecount_WhenCounterAlreadyAssigned_ShouldReopenAssignment() {
            InventoryCount count = pendingCount("9.00");
            InventoryCountAssignment counter = assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            counter.markDone();
            count.addAssignment(counter);
            stubCount(count);

            InventoryTakeResponse response = service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(LINE_ID), COUNTER_ID, "Đếm lại"));

            assertThat(response.assignments()).hasSize(1);
            assertThat(counter.getStatus()).isEqualTo(InventoryCountAssignmentStatus.ASSIGNED);
            assertThat(counter.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("returnForRecount without an assignee reopens all existing counters")
        void returnForRecount_WhenNoAssignee_ShouldReopenAllCounters() {
            InventoryCount count = pendingCount("9.00");
            InventoryCountAssignment first = assignment(COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            InventoryCountAssignment second = assignment(SECOND_COUNTER_ID, InventoryCountAssignmentRole.COUNTER);
            first.markDone();
            second.markDone();
            count.addAssignment(first);
            count.addAssignment(second);
            stubCount(count);

            service.returnForRecount(COUNT_ID, new ReturnForRecountRequest(List.of(LINE_ID), null, "Đếm lại"));

            assertThat(List.of(first.getStatus(), second.getStatus()))
                    .containsOnly(InventoryCountAssignmentStatus.ASSIGNED);
        }

        @Test
        @DisplayName("returnForRecount without reason throws BadRequestException")
        void returnForRecount_WhenReasonBlank_ShouldThrow() {
            stubCount(pendingCount("9.00"));

            assertThatThrownBy(() -> service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(LINE_ID), COUNTER_ID, " ")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Lý do đếm lại là bắt buộc");
        }

        @Test
        @DisplayName("returnForRecount with a line of another count throws BadRequestException")
        void returnForRecount_WhenLineNotInCount_ShouldThrow() {
            stubCount(pendingCount("9.00"));

            assertThatThrownBy(() -> service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(UNKNOWN_ID), COUNTER_ID, "Đếm lại")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Một hoặc nhiều dòng yêu cầu kiểm kê lại không thuộc kỳ này");
        }

        @Test
        @DisplayName("returnForRecount while still counting throws BadRequestException")
        void returnForRecount_WhenCounting_ShouldThrow() {
            stubCount(countingCount("9.00"));

            assertThatThrownBy(() -> service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(LINE_ID), COUNTER_ID, "Đếm lại")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ có thể trả về đếm lại khi kỳ đang chờ phê duyệt");
        }

        @Test
        @DisplayName("returnForRecount by inventory staff throws AccessDeniedException")
        void returnForRecount_WhenInventoryStaff_ShouldDeny() {
            authenticate(COUNTER_ID.toString(), "INVENTORY_STAFF");

            assertThatThrownBy(() -> service.returnForRecount(COUNT_ID,
                    new ReturnForRecountRequest(List.of(LINE_ID), COUNTER_ID, "Đếm lại")))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelTests {

        @ParameterizedTest
        @EnumSource(value = InventoryCountStatus.class, names = {"DRAFT", "COUNTING", "PENDING_APPROVAL"})
        @DisplayName("cancel stores the trimmed reason for every open status")
        void cancel_WhenOpen_ShouldCancel(InventoryCountStatus status) {
            InventoryCount count = count(status);
            stubCount(count);

            InventoryTakeResponse response = service.cancel(COUNT_ID, " Đổi lịch kiểm kê ");

            assertThat(response.status()).isEqualTo(InventoryCountStatus.CANCELLED);
            assertThat(response.cancelReason()).isEqualTo("Đổi lịch kiểm kê");
            assertThat(response.cancelledBy()).isEqualTo(MANAGER_ID);
            assertThat(response.cancelledAt()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(value = InventoryCountStatus.class, names = {"COMPLETED", "CANCELLED"})
        @DisplayName("cancel a finished count throws BadRequestException")
        void cancel_WhenClosed_ShouldThrow(InventoryCountStatus status) {
            stubCount(count(status));

            assertThatThrownBy(() -> service.cancel(COUNT_ID, "Hủy muộn"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Không thể hủy kỳ kiểm kê ở trạng thái hiện tại");
        }

        @Test
        @DisplayName("cancel without reason throws BadRequestException")
        void cancel_WhenReasonBlank_ShouldThrow() {
            stubCount(count(InventoryCountStatus.DRAFT));

            assertThatThrownBy(() -> service.cancel(COUNT_ID, "  "))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Lý do hủy kỳ kiểm kê là bắt buộc");
        }
    }

    // ====== Fixtures ======

    private void authenticate(String userId, String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(userId, "kho-test", "test", Set.of(role), "corr-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void stubCount(InventoryCount count) {
        when(countRepository.findByIdAndIsDeletedFalse(COUNT_ID)).thenReturn(Optional.of(count));
    }

    private InventoryCount count(InventoryCountStatus status) {
        InventoryCount count = new InventoryCount();
        count.setId(COUNT_ID);
        count.setCountCode("CT-20261103-1A2B3C4D");
        count.setBranchId(BRANCH_ID);
        count.setScopeType(InventoryCountScopeType.FULL);
        count.setStatus(status);
        return count;
    }

    private InventoryCount countingCount(String counted) {
        InventoryCount count = count(InventoryCountStatus.COUNTING);
        count.setStartedAt(Instant.parse("2026-11-03T01:00:00Z"));
        InventoryCountLine line = line(LINE_ID, location, "10.00");
        line.recordCount(new BigDecimal(counted), null, null);
        count.addLine(line);
        return count;
    }

    private InventoryCount pendingCount(String counted) {
        InventoryCount count = countingCount(counted);
        count.getLines().get(0).takeCostSnapshot(new BigDecimal("50000"));
        count.setStatus(InventoryCountStatus.PENDING_APPROVAL);
        return count;
    }

    private InventoryCountLine line(UUID id, InventoryLocation lineLocation, String snapshot) {
        InventoryCountLine line = new InventoryCountLine();
        line.setId(id);
        line.setInventoryItem(item);
        line.setLocation(lineLocation);
        line.takeSnapshot(new BigDecimal(snapshot));
        return line;
    }

    private InventoryCountAssignment assignment(UUID userId, InventoryCountAssignmentRole role) {
        InventoryCountAssignment assignment = new InventoryCountAssignment();
        assignment.setUserId(userId);
        assignment.setRole(role);
        return assignment;
    }

    private InventoryLocation location(UUID id, UUID branchId) {
        InventoryLocation value = new InventoryLocation();
        value.setId(id);
        value.setBranchId(branchId);
        value.setCode("KE-A1");
        value.setStatus(InventoryStatus.ACTIVE);
        return value;
    }

    private InventoryBalance balance(InventoryBatch balanceBatch, String quantity) {
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(balanceBatch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(quantity));
        return balance;
    }

    private VariantResponse variant(String costPrice) {
        return new VariantResponse(VARIANT_ID, null, "Trà Shan Tuyết", "SP-00001", "TRA-HOP-00001", "Hộp 100g",
                new BigDecimal("120000"), new BigDecimal(costPrice), null, null, null, null, null, "ACTIVE", "ACTIVE", true);
    }

    private CreateInventoryTakeRequest request(InventoryCountScopeType scopeType, List<UUID> locationIds,
                                               List<UUID> itemIds, List<UUID> batchIds, UUID supervisorId) {
        return new CreateInventoryTakeRequest(BRANCH_ID, null, null, scopeType, locationIds, itemIds, batchIds,
                null, supervisorId, null, null);
    }

    private UpdateInventoryCountsRequest counts(CountLineUpdate... updates) {
        return new UpdateInventoryCountsRequest(List.of(updates));
    }

    private CountLineUpdate update(UUID lineId, String counted, String good) {
        return new CountLineUpdate(lineId, new BigDecimal(counted), good == null ? null : new BigDecimal(good),
                null, null, null, null, null);
    }
}
