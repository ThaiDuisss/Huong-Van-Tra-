package org.hvtra.iventoryservice.bundle.service.impl;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.bundle.dto.request.CancelCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.ConfirmCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CreateCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleLineRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.bundle.mapper.CustomBundlePackingMapper;
import org.hvtra.iventoryservice.bundle.repository.CustomBundlePackingRepository;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService;
import org.hvtra.iventoryservice.bundle.service.CustomBundleEventPublisher;
import org.hvtra.iventoryservice.bundle.service.CustomBundlePackingService;
import org.hvtra.iventoryservice.bundle.service.MaterialReorderAlertService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomBundlePackingServiceImpl implements CustomBundlePackingService {

    private static final String PACKING_SEQUENCE_KEY = "DGB";
    private static final String ISSUE_SEQUENCE_KEY = "PXB";
    private static final Role[] WAREHOUSE_ROLES = {Role.SUPER_ADMIN, Role.MANAGER, Role.INVENTORY_STAFF};

    private final CustomBundlePackingRepository repository;
    private final CustomBundleAvailabilityService availabilityService;
    private final InventoryReservationService reservationService;
    private final MaterialReorderAlertService reorderAlertService;
    private final CustomBundleEventPublisher eventPublisher;
    private final BusinessSequenceService sequenceService;
    private final CustomBundlePackingMapper mapper;

    public CustomBundlePackingServiceImpl(
            CustomBundlePackingRepository repository,
            CustomBundleAvailabilityService availabilityService,
            InventoryReservationService reservationService,
            MaterialReorderAlertService reorderAlertService,
            CustomBundleEventPublisher eventPublisher,
            BusinessSequenceService sequenceService,
            CustomBundlePackingMapper mapper) {
        this.repository = repository;
        this.availabilityService = availabilityService;
        this.reservationService = reservationService;
        this.reorderAlertService = reorderAlertService;
        this.eventPublisher = eventPublisher;
        this.sequenceService = sequenceService;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CustomBundlePackingResponse create(CreateCustomBundlePackingRequest request) {
        var existing = repository.findByOrderItemId(request.orderItemId());
        if (existing.isPresent()) {
            return findById(existing.get().getId());
        }

        CustomBundleAvailabilityService.Evaluation evaluation =
                availabilityService.evaluate(request.branchId(), request.lines());

        CustomBundlePacking packing = new CustomBundlePacking();
        packing.setPackingCode(sequenceService.nextCode(PACKING_SEQUENCE_KEY, 5));
        packing.setOrderId(request.orderId());
        packing.setOrderCode(request.orderCode());
        packing.setOrderItemId(request.orderItemId());
        packing.setProductVariantId(request.productVariantId());
        packing.setBundleName(request.bundleName());
        packing.setBranchId(request.branchId());
        packing.setLocationId(evaluation.location().getId());
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (CustomBundleAvailabilityService.Material material : evaluation.materials()) {
            CustomBundlePackingLine line = new CustomBundlePackingLine();
            line.setIngredientId(material.estimate().ingredientId());
            line.setInventoryItem(material.item());
            line.setIngredientName(material.item().getName());
            line.setRequiredQuantity(material.estimate().requiredQuantity());
            packing.addLine(line);
            totalWeight = totalWeight.add(material.estimate().requiredQuantity());
        }
        packing.setExpectedTotalWeight(totalWeight);
        // Lưu trước để có id: id này là khóa giữ chỗ nguyên liệu của yêu cầu.
        repository.saveAndFlush(packing);

        applyAvailability(packing, evaluation);
        repository.save(packing);
        return findById(packing.getId());
    }

    @Override
    @Transactional
    public CustomBundlePackingResponse recheck(UUID id) {
        requireAnyRole(WAREHOUSE_ROLES);
        CustomBundlePacking packing = lock(id);
        if (packing.getStatus() != CustomBundlePackingStatus.WAITING_MATERIAL) {
            return findById(id);
        }
        CustomBundleAvailabilityService.Evaluation evaluation = availabilityService.evaluate(
                packing.getBranchId(),
                packing.getLines().stream()
                        .map(line -> new CustomBundleLineRequest(
                                line.getIngredientId(), line.getIngredientName(), line.getRequiredQuantity()))
                        .toList());
        applyAvailability(packing, evaluation);
        repository.save(packing);
        return findById(id);
    }

    @Override
    @Transactional
    public CustomBundlePackingResponse confirm(UUID id, ConfirmCustomBundlePackingRequest request) {
        requireAnyRole(WAREHOUSE_ROLES);
        CustomBundlePacking packing = lock(id);
        if (packing.getStatus() == CustomBundlePackingStatus.PACKED) {
            return findById(id);
        }
        if (packing.getStatus() != CustomBundlePackingStatus.AWAITING_PACKING) {
            throw new BadRequestException("Chỉ đóng gói được yêu cầu đang ở trạng thái chờ đóng gói");
        }
        Set<UUID> picked = new HashSet<>(request.pickedLineIds());
        for (CustomBundlePackingLine line : packing.getLines()) {
            if (!picked.contains(line.getId())) {
                throw new BadRequestException("Chưa xác nhận lấy đủ nguyên liệu " + line.getIngredientName());
            }
            line.setPicked(true);
        }

        String issueCode = sequenceService.nextCode(ISSUE_SEQUENCE_KEY, 5);
        reservationService.consumeMaterials(
                packing.getId(),
                InventoryTransactionType.CUSTOM_BUNDLE_ISSUE,
                InventoryReferenceType.CUSTOM_BUNDLE_PACKING,
                packing.getId().toString(),
                issueCode);

        packing.setPackagingType(trimToNull(request.packagingType()));
        packing.setNumberOfPackages(request.numberOfPackages());
        packing.setActualTotalWeight(request.actualTotalWeight());
        packing.setPackingNote(trimToNull(request.note()));
        packing.setIssueTransactionCode(issueCode);
        packing.setPackedBy(currentUserIdOrNull());
        packing.setPackedAt(Instant.now());
        packing.setStatus(CustomBundlePackingStatus.PACKED);
        repository.save(packing);
        eventPublisher.publishPacked(packing);
        return findById(id);
    }

    @Override
    @Transactional
    public List<CustomBundlePackingResponse> cancelByOrder(CancelCustomBundlePackingRequest request) {
        List<CustomBundlePackingResponse> result = new ArrayList<>();
        for (CustomBundlePacking candidate : repository.findByOrderId(request.orderId())) {
            CustomBundlePacking packing = lock(candidate.getId());
            switch (packing.getStatus()) {
                case PACKED -> throw new BadRequestException(
                        "Bundle " + packing.getPackingCode() + " đã đóng gói, không huỷ được bằng thao tác huỷ đơn");
                case AWAITING_PACKING -> {
                    reservationService.releaseMaterials(new ReleaseInventoryReservationOrderRequest(packing.getId()));
                    markCancelled(packing, request.reason());
                }
                case WAITING_MATERIAL -> markCancelled(packing, request.reason());
                case CANCELLED -> {
                    // đã huỷ trước đó: giữ nguyên
                }
            }
            result.add(mapper.toSummary(packing));
        }
        return result;
    }

    @Override
    public PageResponse<CustomBundlePackingResponse> findAll(
            CustomBundlePackingStatus status, UUID branchId, String search, Pageable pageable) {
        Specification<CustomBundlePacking> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("packingCode")), pattern),
                        cb.like(cb.lower(root.get("orderCode")), pattern),
                        cb.like(cb.lower(root.get("bundleName")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toSummary));
    }

    @Override
    public CustomBundlePackingResponse findById(UUID id) {
        return mapper.toResponse(repository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custom bundle packing", id)));
    }

    @Override
    public List<CustomBundlePackingResponse> findByOrder(UUID orderId) {
        return repository.findByOrderId(orderId).stream().map(mapper::toSummary).toList();
    }

    /** Đủ nguyên liệu: giữ chỗ (khóa = id yêu cầu) → chờ đóng gói. Thiếu: chờ nguyên liệu + cảnh báo nhập. */
    private void applyAvailability(CustomBundlePacking packing, CustomBundleAvailabilityService.Evaluation evaluation) {
        if (evaluation.enough()) {
            reservationService.reserveMaterials(new ReserveMaterialOrderRequest(
                    packing.getId(),
                    packing.getBranchId(),
                    0,
                    null,
                    packing.getLines().stream()
                            .map(line -> new ReserveMaterialLineRequest(
                                    packing.getOrderItemId(),
                                    packing.getProductVariantId(),
                                    line.getIngredientId(),
                                    line.getInventoryItem().getId(),
                                    null,
                                    line.getRequiredQuantity()))
                            .toList()));
            packing.setStatus(CustomBundlePackingStatus.AWAITING_PACKING);
            packing.setReservedAt(Instant.now());
            packing.setExpectedAvailableAt(null);
            return;
        }
        packing.setStatus(CustomBundlePackingStatus.WAITING_MATERIAL);
        packing.setExpectedAvailableAt(evaluation.response().estimatedAvailableAt());
        evaluation.materials().forEach(material ->
                reorderAlertService.openIfMissing(material.item(), evaluation.location(), material.estimate()));
    }

    private void markCancelled(CustomBundlePacking packing, String reason) {
        packing.setStatus(CustomBundlePackingStatus.CANCELLED);
        packing.setCancelledAt(Instant.now());
        packing.setCancelledBy(currentUserIdOrNull());
        packing.setCancelReason(trimToNull(reason));
        repository.save(packing);
    }

    private CustomBundlePacking lock(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custom bundle packing", id));
    }

    private void requireAnyRole(Role... roles) {
        if (SecurityUtils.hasAnyRole(roles)) return;
        throw new AccessDeniedException("Bạn không có quyền thao tác yêu cầu đóng trà theo yêu cầu");
    }

    private static UUID currentUserIdOrNull() {
        try {
            return UUID.fromString(SecurityUtils.currentUserId());
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
