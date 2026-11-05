package org.hvtra.iventoryservice.bundle;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.bundle.dto.request.CancelCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.ConfirmCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CreateCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleLineRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.bundle.mapper.CustomBundlePackingMapper;
import org.hvtra.iventoryservice.bundle.repository.CustomBundlePackingRepository;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService.Evaluation;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService.Material;
import org.hvtra.iventoryservice.bundle.service.CustomBundleEventPublisher;
import org.hvtra.iventoryservice.bundle.service.MaterialReorderAlertService;
import org.hvtra.iventoryservice.bundle.service.impl.CustomBundlePackingServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePacking;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePackingLine;
import org.hvtra.iventoryservice.domain.enums.CustomBundlePackingStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialLineRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReserveMaterialOrderRequest;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.ArrayList;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CustomBundlePackingServiceImpl Unit Tests")
class CustomBundlePackingServiceImplTest {

    private static final UUID PACKING_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440600");
    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440700");
    private static final UUID ORDER_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440701");
    private static final UUID VARIANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440702");
    private static final UUID BRANCH_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    private static final UUID WAREHOUSE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");
    private static final UUID STAFF_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    private static final UUID SHAN_INGREDIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440800");
    private static final UUID LOTUS_INGREDIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440801");
    private static final UUID SHAN_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440810");
    private static final UUID LOTUS_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440811");
    private static final UUID SHAN_LINE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440820");
    private static final UUID LOTUS_LINE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440821");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Instant ETA = Instant.parse("2026-11-05T02:00:00Z");

    @Mock
    private CustomBundlePackingRepository repository;
    @Mock
    private CustomBundleAvailabilityService availabilityService;
    @Mock
    private InventoryReservationService reservationService;
    @Mock
    private MaterialReorderAlertService reorderAlertService;
    @Mock
    private CustomBundleEventPublisher eventPublisher;
    @Mock
    private BusinessSequenceService sequenceService;

    private CustomBundlePackingServiceImpl service;
    private InventoryLocation warehouse;
    private InventoryItem shanTuyet;
    private InventoryItem lotusPetal;

    @BeforeEach
    void setUp() {
        service = new CustomBundlePackingServiceImpl(repository, availabilityService, reservationService,
                reorderAlertService, eventPublisher, sequenceService, new CustomBundlePackingMapper());

        warehouse = new InventoryLocation();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setBranchId(BRANCH_ID);
        shanTuyet = item(SHAN_ITEM_ID, SHAN_INGREDIENT_ID, "NL-00001", "Trà Shan Tuyết");
        lotusPetal = item(LOTUS_ITEM_ID, LOTUS_INGREDIENT_ID, "NL-00002", "Cánh sen sấy");

        when(sequenceService.nextCode("DGB", 5)).thenReturn("DGB-00001");
        when(sequenceService.nextCode("PXB", 5)).thenReturn("PXB-00001");
        when(repository.saveAndFlush(any(CustomBundlePacking.class))).thenAnswer(invocation -> {
            CustomBundlePacking packing = invocation.getArgument(0);
            packing.setId(PACKING_ID);
            when(repository.findByIdWithLines(PACKING_ID)).thenReturn(Optional.of(packing));
            return packing;
        });
        when(repository.save(any(CustomBundlePacking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByOrderItemId(any())).thenReturn(Optional.empty());
        authenticate(STAFF_ID.toString(), "INVENTORY_STAFF");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("create with enough materials reserves them and waits for packing")
        void create_WhenMaterialsEnough_ShouldReserveAndAwaitPacking() {
            when(availabilityService.evaluate(eq(BRANCH_ID), anyList())).thenReturn(evaluation(true));

            CustomBundlePackingResponse response = service.create(createRequest());

            assertThat(response.packingCode()).isEqualTo("DGB-00001");
            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.AWAITING_PACKING);
            assertThat(response.locationId()).isEqualTo(WAREHOUSE_ID);
            assertThat(response.expectedTotalWeight()).isEqualByComparingTo("0.08");
            assertThat(response.reservedAt()).isNotNull();
            assertThat(response.lines()).extracting(CustomBundlePackingResponse.Line::ingredientCode)
                    .containsExactly("NL-00001", "NL-00002");

            ArgumentCaptor<ReserveMaterialOrderRequest> reserve = ArgumentCaptor.forClass(ReserveMaterialOrderRequest.class);
            verify(reservationService).reserveMaterials(reserve.capture());
            assertThat(reserve.getValue().orderId()).isEqualTo(PACKING_ID);
            assertThat(reserve.getValue().branchId()).isEqualTo(BRANCH_ID);
            assertThat(reserve.getValue().lines()).extracting(ReserveMaterialLineRequest::inventoryItemId, ReserveMaterialLineRequest::quantity)
                    .containsExactly(
                            tuple(SHAN_ITEM_ID, new BigDecimal("0.05")),
                            tuple(LOTUS_ITEM_ID, new BigDecimal("0.03")));
            verifyNoInteractions(reorderAlertService);
        }

        @Test
        @DisplayName("create with missing materials waits for material and opens reorder alerts")
        void create_WhenMaterialsMissing_ShouldWaitForMaterial() {
            Evaluation evaluation = evaluation(false);
            when(availabilityService.evaluate(eq(BRANCH_ID), anyList())).thenReturn(evaluation);

            CustomBundlePackingResponse response = service.create(createRequest());

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.WAITING_MATERIAL);
            assertThat(response.expectedAvailableAt()).isEqualTo(ETA);
            assertThat(response.reservedAt()).isNull();
            verify(reorderAlertService, times(2)).openIfMissing(any(), eq(warehouse), any());
            verify(reservationService, never()).reserveMaterials(any());
        }

        @Test
        @DisplayName("create is idempotent per order item")
        void create_WhenOrderItemAlreadyHasPacking_ShouldReturnExisting() {
            CustomBundlePacking existing = packing(CustomBundlePackingStatus.AWAITING_PACKING);
            when(repository.findByOrderItemId(ORDER_ITEM_ID)).thenReturn(Optional.of(existing));
            when(repository.findByIdWithLines(PACKING_ID)).thenReturn(Optional.of(existing));

            CustomBundlePackingResponse response = service.create(createRequest());

            assertThat(response.id()).isEqualTo(PACKING_ID);
            verifyNoInteractions(availabilityService, reservationService);
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("create with an ingredient not stocked propagates BadRequestException")
        void create_WhenIngredientNotStocked_ShouldThrow() {
            when(availabilityService.evaluate(eq(BRANCH_ID), anyList()))
                    .thenThrow(new BadRequestException("Nguyên liệu Trà Shan Tuyết chưa có trong kho"));

            assertThatThrownBy(() -> service.create(createRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Nguyên liệu Trà Shan Tuyết chưa có trong kho");
            verify(repository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("recheck")
    class RecheckTests {

        @Test
        @DisplayName("recheck reserves materials once they arrive")
        void recheck_WhenMaterialsArrived_ShouldReserve() {
            CustomBundlePacking packing = stubLocked(packing(CustomBundlePackingStatus.WAITING_MATERIAL));
            packing.setExpectedAvailableAt(ETA);
            when(availabilityService.evaluate(eq(BRANCH_ID), anyList())).thenReturn(evaluation(true));

            CustomBundlePackingResponse response = service.recheck(PACKING_ID);

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.AWAITING_PACKING);
            assertThat(response.expectedAvailableAt()).isNull();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CustomBundleLineRequest>> lines = ArgumentCaptor.forClass(List.class);
            verify(availabilityService).evaluate(eq(BRANCH_ID), lines.capture());
            assertThat(lines.getValue()).extracting(CustomBundleLineRequest::ingredientId)
                    .containsExactly(SHAN_INGREDIENT_ID, LOTUS_INGREDIENT_ID);
            verify(reservationService).reserveMaterials(any());
        }

        @Test
        @DisplayName("recheck keeps waiting and refreshes the ETA when still short")
        void recheck_WhenStillMissing_ShouldKeepWaiting() {
            stubLocked(packing(CustomBundlePackingStatus.WAITING_MATERIAL));
            when(availabilityService.evaluate(eq(BRANCH_ID), anyList())).thenReturn(evaluation(false));

            CustomBundlePackingResponse response = service.recheck(PACKING_ID);

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.WAITING_MATERIAL);
            assertThat(response.expectedAvailableAt()).isEqualTo(ETA);
            verify(reservationService, never()).reserveMaterials(any());
        }

        @Test
        @DisplayName("recheck leaves packings that are no longer waiting untouched")
        void recheck_WhenNotWaiting_ShouldReturnAsIs() {
            stubLocked(packing(CustomBundlePackingStatus.AWAITING_PACKING));

            CustomBundlePackingResponse response = service.recheck(PACKING_ID);

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.AWAITING_PACKING);
            verifyNoInteractions(availabilityService);
        }

        @Test
        @DisplayName("recheck of unknown packing throws ResourceNotFoundException")
        void recheck_WhenMissing_ShouldThrow() {
            when(repository.findByIdForUpdate(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recheck(UNKNOWN_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Custom bundle packing not found: " + UNKNOWN_ID);
        }

        @Test
        @DisplayName("recheck by sale staff throws AccessDeniedException")
        void recheck_WhenSaleStaff_ShouldDeny() {
            authenticate(STAFF_ID.toString(), "SALE_STAFF");

            assertThatThrownBy(() -> service.recheck(PACKING_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Bạn không có quyền thao tác yêu cầu đóng trà theo yêu cầu");
        }
    }

    @Nested
    @DisplayName("confirm")
    class ConfirmTests {

        @Test
        @DisplayName("confirm consumes reserved materials, records packing details and publishes the event")
        void confirm_WhenAllLinesPicked_ShouldPack() {
            CustomBundlePacking packing = stubLocked(packing(CustomBundlePackingStatus.AWAITING_PACKING));

            CustomBundlePackingResponse response = service.confirm(PACKING_ID, new ConfirmCustomBundlePackingRequest(
                    List.of(SHAN_LINE_ID, LOTUS_LINE_ID), "  Hộp gỗ  ", 2, new BigDecimal("0.081"), " Gói kèm thiệp "));

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.PACKED);
            assertThat(response.issueTransactionCode()).isEqualTo("PXB-00001");
            assertThat(response.packagingType()).isEqualTo("Hộp gỗ");
            assertThat(response.packingNote()).isEqualTo("Gói kèm thiệp");
            assertThat(response.numberOfPackages()).isEqualTo(2);
            assertThat(response.actualTotalWeight()).isEqualByComparingTo("0.081");
            assertThat(response.packedBy()).isEqualTo(STAFF_ID);
            assertThat(response.packedAt()).isNotNull();
            assertThat(response.lines()).allMatch(CustomBundlePackingResponse.Line::picked);
            verify(reservationService).consumeMaterials(PACKING_ID, InventoryTransactionType.CUSTOM_BUNDLE_ISSUE,
                    InventoryReferenceType.CUSTOM_BUNDLE_PACKING, PACKING_ID.toString(), "PXB-00001");
            verify(eventPublisher).publishPacked(packing);
        }

        @Test
        @DisplayName("confirm stores blank packaging type and note as null")
        void confirm_WhenOptionalFieldsBlank_ShouldStoreNull() {
            stubLocked(packing(CustomBundlePackingStatus.AWAITING_PACKING));

            CustomBundlePackingResponse response = service.confirm(PACKING_ID, new ConfirmCustomBundlePackingRequest(
                    List.of(SHAN_LINE_ID, LOTUS_LINE_ID), " ", null, null, ""));

            assertThat(response.packagingType()).isNull();
            assertThat(response.packingNote()).isNull();
        }

        @Test
        @DisplayName("confirm an already packed packing is idempotent")
        void confirm_WhenAlreadyPacked_ShouldReturnWithoutConsuming() {
            stubLocked(packing(CustomBundlePackingStatus.PACKED));

            CustomBundlePackingResponse response = service.confirm(PACKING_ID,
                    new ConfirmCustomBundlePackingRequest(List.of(SHAN_LINE_ID), null, null, null, null));

            assertThat(response.status()).isEqualTo(CustomBundlePackingStatus.PACKED);
            verifyNoInteractions(reservationService, eventPublisher);
        }

        @Test
        @DisplayName("confirm without picking every line throws BadRequestException")
        void confirm_WhenLineNotPicked_ShouldThrow() {
            stubLocked(packing(CustomBundlePackingStatus.AWAITING_PACKING));

            assertThatThrownBy(() -> service.confirm(PACKING_ID,
                    new ConfirmCustomBundlePackingRequest(List.of(SHAN_LINE_ID), null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chưa xác nhận lấy đủ nguyên liệu Cánh sen sấy");
            verifyNoInteractions(reservationService, eventPublisher);
        }

        @Test
        @DisplayName("confirm while waiting for material throws BadRequestException")
        void confirm_WhenWaitingMaterial_ShouldThrow() {
            stubLocked(packing(CustomBundlePackingStatus.WAITING_MATERIAL));

            assertThatThrownBy(() -> service.confirm(PACKING_ID,
                    new ConfirmCustomBundlePackingRequest(List.of(SHAN_LINE_ID, LOTUS_LINE_ID), null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Chỉ đóng gói được yêu cầu đang ở trạng thái chờ đóng gói");
        }
    }

    @Nested
    @DisplayName("cancelByOrder")
    class CancelByOrderTests {

        @Test
        @DisplayName("cancelByOrder releases reserved materials and cancels unpacked packings")
        void cancelByOrder_WhenUnpacked_ShouldCancelAndRelease() {
            CustomBundlePacking awaiting = packing(CustomBundlePackingStatus.AWAITING_PACKING);
            CustomBundlePacking waiting = packing(CustomBundlePackingStatus.WAITING_MATERIAL);
            waiting.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440601"));
            CustomBundlePacking cancelled = packing(CustomBundlePackingStatus.CANCELLED);
            cancelled.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440602"));
            cancelled.setCancelReason("Khách đổi ý");
            when(repository.findByOrderId(ORDER_ID)).thenReturn(List.of(awaiting, waiting, cancelled));
            for (CustomBundlePacking packing : List.of(awaiting, waiting, cancelled)) {
                when(repository.findByIdForUpdate(packing.getId())).thenReturn(Optional.of(packing));
            }

            List<CustomBundlePackingResponse> result = service.cancelByOrder(
                    new CancelCustomBundlePackingRequest(ORDER_ID, "  Khách huỷ đơn  "));

            assertThat(result).extracting(CustomBundlePackingResponse::status).containsOnly(CustomBundlePackingStatus.CANCELLED);
            assertThat(result).extracting(CustomBundlePackingResponse::cancelReason)
                    .containsExactly("Khách huỷ đơn", "Khách huỷ đơn", "Khách đổi ý");
            assertThat(awaiting.getCancelledBy()).isEqualTo(STAFF_ID);
            verify(reservationService).releaseMaterials(new ReleaseInventoryReservationOrderRequest(PACKING_ID));
            verify(reservationService, times(1)).releaseMaterials(any());
        }

        @Test
        @DisplayName("cancelByOrder from an internal call without user keeps cancelledBy empty")
        void cancelByOrder_WhenNoAuthentication_ShouldCancelWithoutUser() {
            SecurityContextHolder.clearContext();
            CustomBundlePacking waiting = packing(CustomBundlePackingStatus.WAITING_MATERIAL);
            when(repository.findByOrderId(ORDER_ID)).thenReturn(List.of(waiting));
            when(repository.findByIdForUpdate(PACKING_ID)).thenReturn(Optional.of(waiting));

            service.cancelByOrder(new CancelCustomBundlePackingRequest(ORDER_ID, null));

            assertThat(waiting.getStatus()).isEqualTo(CustomBundlePackingStatus.CANCELLED);
            assertThat(waiting.getCancelledBy()).isNull();
            assertThat(waiting.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("cancelByOrder returns an empty list for orders without packings")
        void cancelByOrder_WhenNoPacking_ShouldReturnEmpty() {
            when(repository.findByOrderId(ORDER_ID)).thenReturn(List.of());

            assertThat(service.cancelByOrder(new CancelCustomBundlePackingRequest(ORDER_ID, "Huỷ"))).isEmpty();
        }

        @Test
        @DisplayName("cancelByOrder with a packed bundle throws BadRequestException")
        void cancelByOrder_WhenPacked_ShouldThrow() {
            CustomBundlePacking packed = packing(CustomBundlePackingStatus.PACKED);
            when(repository.findByOrderId(ORDER_ID)).thenReturn(List.of(packed));
            when(repository.findByIdForUpdate(PACKING_ID)).thenReturn(Optional.of(packed));

            assertThatThrownBy(() -> service.cancelByOrder(new CancelCustomBundlePackingRequest(ORDER_ID, "Huỷ")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Bundle DGB-00001 đã đóng gói, không huỷ được bằng thao tác huỷ đơn");
            verifyNoInteractions(reservationService);
        }
    }

    @Nested
    @DisplayName("findAll / findById / findByOrder")
    class QueryTests {

        @Test
        @DisplayName("findAll returns summaries without lines")
        void findAll_WhenPackingsMatch_ShouldReturnSummaries() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(repository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(packing(CustomBundlePackingStatus.AWAITING_PACKING)), pageable, 1));

            PageResponse<CustomBundlePackingResponse> page = service.findAll(
                    CustomBundlePackingStatus.AWAITING_PACKING, BRANCH_ID, "DH-00001", pageable);

            assertThat(page.content()).singleElement().satisfies(summary -> {
                assertThat(summary.packingCode()).isEqualTo("DGB-00001");
                assertThat(summary.lines()).isEmpty();
            });
        }

        @Test
        @DisplayName("findAll returns an empty page when nothing matches")
        void findAll_WhenNothingMatches_ShouldReturnEmptyPage() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            assertThat(service.findAll(null, null, null, pageable).content()).isEmpty();
        }

        @Test
        @DisplayName("findById returns the packing with ingredient lines")
        void findById_WhenExists_ShouldReturnDetail() {
            when(repository.findByIdWithLines(PACKING_ID)).thenReturn(Optional.of(packing(CustomBundlePackingStatus.AWAITING_PACKING)));

            CustomBundlePackingResponse response = service.findById(PACKING_ID);

            assertThat(response.orderCode()).isEqualTo("DH-00001");
            assertThat(response.lines()).extracting(CustomBundlePackingResponse.Line::ingredientName)
                    .containsExactly("Trà Shan Tuyết", "Cánh sen sấy");
        }

        @Test
        @DisplayName("findById throws ResourceNotFoundException for unknown id")
        void findById_WhenMissing_ShouldThrow() {
            when(repository.findByIdWithLines(UNKNOWN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(UNKNOWN_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Custom bundle packing not found: " + UNKNOWN_ID);
        }

        @Test
        @DisplayName("findByOrder returns every packing of the order")
        void findByOrder_WhenOrderHasPackings_ShouldReturnSummaries() {
            when(repository.findByOrderId(ORDER_ID)).thenReturn(List.of(packing(CustomBundlePackingStatus.PACKED)));

            assertThat(service.findByOrder(ORDER_ID)).singleElement()
                    .extracting(CustomBundlePackingResponse::status)
                    .isEqualTo(CustomBundlePackingStatus.PACKED);
        }

        @Test
        @DisplayName("findByOrder returns an empty list for orders without bundles")
        void findByOrder_WhenNone_ShouldReturnEmpty() {
            when(repository.findByOrderId(UNKNOWN_ID)).thenReturn(List.of());

            assertThat(service.findByOrder(UNKNOWN_ID)).isEmpty();
        }
    }

    // ====== Fixtures ======

    private void authenticate(String userId, String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(userId, "kho-test", "test", Set.of(role), "corr-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private CustomBundlePacking stubLocked(CustomBundlePacking packing) {
        when(repository.findByIdForUpdate(PACKING_ID)).thenReturn(Optional.of(packing));
        when(repository.findByIdWithLines(PACKING_ID)).thenReturn(Optional.of(packing));
        return packing;
    }

    private CreateCustomBundlePackingRequest createRequest() {
        return new CreateCustomBundlePackingRequest(ORDER_ID, "DH-00001", ORDER_ITEM_ID, VARIANT_ID,
                "Trà sen Shan Tuyết theo yêu cầu", BRANCH_ID, List.of(
                new CustomBundleLineRequest(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", new BigDecimal("0.05")),
                new CustomBundleLineRequest(LOTUS_INGREDIENT_ID, "Cánh sen sấy", new BigDecimal("0.03"))));
    }

    private Evaluation evaluation(boolean enough) {
        String missing = enough ? "0" : "0.02";
        List<Material> materials = List.of(
                new Material(shanTuyet, estimate(SHAN_INGREDIENT_ID, "Trà Shan Tuyết", "0.05", missing)),
                new Material(lotusPetal, estimate(LOTUS_INGREDIENT_ID, "Cánh sen sấy", "0.03", "0")));
        return new Evaluation(warehouse, materials, new CustomBundleAvailabilityResponse(
                enough, enough ? null : ETA, enough ? 0 : 2, List.of()));
    }

    private MaterialEstimate estimate(UUID ingredientId, String name, String required, String missing) {
        BigDecimal requiredQuantity = new BigDecimal(required);
        BigDecimal missingQuantity = new BigDecimal(missing);
        return new MaterialEstimate(ingredientId, name, requiredQuantity, requiredQuantity.subtract(missingQuantity),
                missingQuantity, missingQuantity.signum() > 0 ? ETA : null, null, null);
    }

    private CustomBundlePacking packing(CustomBundlePackingStatus status) {
        CustomBundlePacking packing = new CustomBundlePacking();
        packing.setId(PACKING_ID);
        packing.setPackingCode("DGB-00001");
        packing.setOrderId(ORDER_ID);
        packing.setOrderCode("DH-00001");
        packing.setOrderItemId(ORDER_ITEM_ID);
        packing.setProductVariantId(VARIANT_ID);
        packing.setBundleName("Trà sen Shan Tuyết theo yêu cầu");
        packing.setBranchId(BRANCH_ID);
        packing.setLocationId(WAREHOUSE_ID);
        packing.setStatus(status);
        packing.setLines(new ArrayList<>());
        packing.addLine(line(SHAN_LINE_ID, shanTuyet, SHAN_INGREDIENT_ID, "0.05"));
        packing.addLine(line(LOTUS_LINE_ID, lotusPetal, LOTUS_INGREDIENT_ID, "0.03"));
        return packing;
    }

    private CustomBundlePackingLine line(UUID id, InventoryItem lineItem, UUID ingredientId, String quantity) {
        CustomBundlePackingLine line = new CustomBundlePackingLine();
        line.setId(id);
        line.setIngredientId(ingredientId);
        line.setInventoryItem(lineItem);
        line.setIngredientName(lineItem.getName());
        line.setRequiredQuantity(new BigDecimal(quantity));
        return line;
    }

    private InventoryItem item(UUID id, UUID ingredientId, String code, String name) {
        InventoryItem value = new InventoryItem();
        value.setId(id);
        value.setReferenceId(ingredientId);
        value.setReferenceCode(code);
        value.setName(name);
        return value;
    }
}
