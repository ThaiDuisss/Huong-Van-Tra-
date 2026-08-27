package org.hvtra.iventoryservice.counting.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.request.RequestInventoryRecountRequest;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.counting.mapper.InventoryTakeMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryBatchBalanceRepository;
import org.hvtra.iventoryservice.counting.repository.InventoryCountRepository;
import org.hvtra.iventoryservice.counting.repository.InventoryCountApprovalPolicyRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatchBalance;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountAssignment;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountAttempt;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionLineRepository;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentRole;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentStatus;

@Service
@Transactional(readOnly = true)
public class InventoryTakeServiceImpl implements InventoryTakeService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryCountRepository countRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBatchBalanceRepository batchBalanceRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryTakeMapper mapper;
    private final InventoryTransactionLineRepository transactionLineRepository;
    private final CatalogClient catalogClient;
    private final InventoryCountApprovalPolicyRepository approvalPolicyRepository;

    public InventoryTakeServiceImpl(
            InventoryCountRepository countRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryBatchBalanceRepository batchBalanceRepository,
            InventoryLocationRepository locationRepository,
            InventoryItemRepository itemRepository,
            InventoryTakeMapper mapper,
            InventoryTransactionLineRepository transactionLineRepository,
            CatalogClient catalogClient,
            InventoryCountApprovalPolicyRepository approvalPolicyRepository) {
        this.countRepository = countRepository;
        this.balanceRepository = balanceRepository;
        this.batchBalanceRepository = batchBalanceRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
        this.mapper = mapper;
        this.transactionLineRepository = transactionLineRepository;
        this.catalogClient = catalogClient;
        this.approvalPolicyRepository = approvalPolicyRepository;
    }

    @Override
    public PageResponse<InventoryTakeResponse> findAll(
            UUID branchId, InventoryCountStatus status,
            InventoryCountScopeType scopeType, Pageable pageable) {
        Specification<InventoryCount> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (scopeType != null) predicates.add(cb.equal(root.get("scopeType"), scopeType));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(
                countRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryTakeResponse findById(UUID id) {
        InventoryCount count = findCountOrThrow(id);
        return mapper.toResponse(count);
    }

    @Override
    @Transactional
    public InventoryTakeResponse create(CreateInventoryTakeRequest request) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        validateScopeInput(request);

        InventoryCount count = new InventoryCount();
        count.setCountCode(generateCountCode());
        count.setBranchId(request.branchId());
        count.setWarehouseId(request.warehouseId());
        count.setCountType(request.countType() != null ? request.countType() : inferCountType(request.scopeType()));
        count.setScopeType(request.scopeType());
        count.setSupervisorId(request.supervisorId());
        count.setBlindCount(Boolean.TRUE.equals(request.blindCount()));
        count.setNote(request.note());
        if (request.locationIds() != null) {
            count.getScopeLocationIds().addAll(request.locationIds());
        }
        if (request.inventoryItemIds() != null) {
            count.getScopeInventoryItemIds().addAll(request.inventoryItemIds());
        }
        if (request.batchIds() != null) {
            count.getScopeBatchIds().addAll(request.batchIds());
        }
        if (request.assignedUserIds() != null) {
            request.assignedUserIds().stream().distinct().forEach(userId -> {
                InventoryCountAssignment assignment = new InventoryCountAssignment();
                assignment.setUserId(userId);
                assignment.setRole(InventoryCountAssignmentRole.COUNTER);
                count.addAssignment(assignment);
            });
        }
        if (request.supervisorId() != null) {
            InventoryCountAssignment assignment = new InventoryCountAssignment();
            assignment.setUserId(request.supervisorId());
            assignment.setRole(InventoryCountAssignmentRole.SUPERVISOR);
            count.addAssignment(assignment);
        }

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse freeze(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        ensureNoOverlappingOpenCount(count);
        count.freeze();
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse start(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "WAREHOUSE_STAFF", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);

        // Delegate state transition to entity
        count.start();

        // === Orchestration: generate lines from balance data ===

        // 1. Lines từ InventoryBalance (item × location, không phân biệt batch)
        List<InventoryBalance> balances = queryBalancesForScope(count);

        // 2. Lines từ InventoryBatchBalance (item × location × batch)
        List<InventoryBatchBalance> batchBalances = queryBatchBalancesForScope(count);

        if (balances.isEmpty() && batchBalances.isEmpty()) {
            throw new BadRequestException("Không tìm thấy tồn kho để kiểm kê");
        }

        // Generate lines cho non-batch balances
        for (InventoryBalance balance : balances) {
            // Kiểm tra xem item+location này đã có batch balance chưa
            // Nếu có → sẽ generate lines chi tiết theo batch, skip tạo line tổng
            boolean hasBatchLines = batchBalances.stream().anyMatch(bb ->
                    bb.getInventoryBatch().getInventoryItem().getId()
                            .equals(balance.getInventoryItem().getId())
                    && bb.getLocation().getId()
                            .equals(balance.getLocation().getId()));

            if (!hasBatchLines) {
                InventoryCountLine line = new InventoryCountLine();
                line.setInventoryItem(balance.getInventoryItem());
                line.setLocation(balance.getLocation());
                // inventoryBatch = null (non-batch tracking)
                line.takeSnapshot(balance.getQuantityOnHand());
                count.addLine(line);
            }
        }

        // Generate lines cho batch balances (1 line per item × location × batch)
        for (InventoryBatchBalance batchBalance : batchBalances) {
            InventoryCountLine line = new InventoryCountLine();
            line.setInventoryItem(batchBalance.getInventoryBatch().getInventoryItem());
            line.setLocation(batchBalance.getLocation());
            line.setInventoryBatch(batchBalance.getInventoryBatch());
            line.takeSnapshot(batchBalance.getQuantityOnHand());
            count.addLine(line);
        }

        snapshotUnitCosts(count);

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse updateCounts(UUID id, UpdateInventoryCountsRequest request) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "WAREHOUSE_STAFF", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        UUID currentUserId = currentUserId();
        ensureCounterCanUpdate(count, currentUserId, request);

        // Delegate validation to entity
        count.validateCanRecordCounts();

        for (UpdateInventoryCountsRequest.CountLineUpdate lineUpdate : request.lines()) {
            InventoryCountLine line = count.getLines().stream()
                    .filter(l -> l.getId().equals(lineUpdate.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory count line", lineUpdate.lineId()));

            // Delegate recording to entity
            line.recordCount(lineUpdate.countedQuantity(), lineUpdate.reasonCode(), lineUpdate.note());
            line.recordConditions(
                    lineUpdate.goodQuantity(),
                    lineUpdate.damagedQuantity(),
                    lineUpdate.expiredQuantity(),
                    lineUpdate.quarantinedQuantity());
            InventoryCountAttempt attempt = new InventoryCountAttempt();
            attempt.setAttemptNo(line.getAttempts().size() + 1);
            attempt.setCounterId(currentUserId);
            attempt.setCountedQuantity(lineUpdate.countedQuantity());
            attempt.setNote(lineUpdate.note());
            line.addAttempt(attempt);
        }

        if (count.getLines().stream().allMatch(InventoryCountLine::hasBeenCounted)) {
            count.getAssignments().stream()
                    .filter(assignment -> assignment.getRole() == InventoryCountAssignmentRole.COUNTER)
                    .filter(assignment -> assignment.getUserId().equals(currentUserId))
                    .forEach(InventoryCountAssignment::markCompleted);
        }

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse requestRecount(UUID id, RequestInventoryRecountRequest request) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BadRequestException("Lý do kiểm kê lại là bắt buộc");
        }
        var requestedIds = new HashSet<>(request.lineIds());
        List<InventoryCountLine> requestedLines = count.getLines().stream()
                .filter(line -> requestedIds.contains(line.getId()))
                .toList();
        if (requestedLines.size() != requestedIds.size()) {
            throw new BadRequestException("Một hoặc nhiều dòng yêu cầu kiểm kê lại không thuộc kỳ này");
        }
        requestedLines.forEach(InventoryCountLine::requestRecount);
        boolean alreadyAssigned = count.getAssignments().stream().anyMatch(assignment ->
                assignment.getRole() == InventoryCountAssignmentRole.COUNTER
                        && assignment.getUserId().equals(request.assignedUserId()));
        if (!alreadyAssigned) {
            InventoryCountAssignment assignment = new InventoryCountAssignment();
            assignment.setUserId(request.assignedUserId());
            assignment.setRole(InventoryCountAssignmentRole.COUNTER);
            count.addAssignment(assignment);
        }
        count.reopenForRecount();
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse finish(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "WAREHOUSE_STAFF", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        ensureAssignedCounter(count, currentUserId());
        reconcileMovements(count);
        count.submitForReconciliation();
        if (count.getLinesWithVariance().isEmpty()) {
            count.submitForApproval(false, currentUserId());
        }
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse submitApproval(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        reconcileMovements(count);
        count.submitForApproval(requiresApproval(count), currentUserId());
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse approve(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.approve(currentUserId());
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse returnForRecount(UUID id, String reason) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.returnForRecount(currentUserId(), reason);
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse reopen(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.reopenReturnedCount();
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse reject(UUID id, String reason) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.reject(currentUserId(), reason);
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse cancel(UUID id, String reason) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.cancel(currentUserId(), reason);
        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse close(UUID id) {
        requireAnyRole("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");
        InventoryCount count = findCountOrThrow(id);
        count.close(currentUserId());
        return mapper.toResponse(countRepository.save(count));
    }

    // ====== Private Helpers ======

    private InventoryCount findCountOrThrow(UUID id) {
        return countRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory count", id));
    }

    private void reconcileMovements(InventoryCount count) {
        if (count.getSnapshotAt() == null) {
            throw new BadRequestException("Kỳ kiểm kê chưa có snapshot");
        }
        count.getLines().forEach(line -> line.reconcileMovements(
                line.getInventoryBatch() == null
                        ? transactionLineRepository.sumMovementAfterSnapshot(
                                line.getInventoryItem().getId(),
                                line.getLocation().getId(),
                                count.getSnapshotAt())
                        : transactionLineRepository.sumBatchMovementAfterSnapshot(
                                line.getInventoryBatch().getId(),
                                line.getLocation().getId(),
                                count.getSnapshotAt())));
    }

    private void snapshotUnitCosts(InventoryCount count) {
        List<UUID> variantIds = count.getLines().stream()
                .map(line -> line.getInventoryItem().getReferenceId())
                .distinct()
                .toList();
        if (variantIds.isEmpty()) return;
        var variants = catalogClient.getVariantsBatch(variantIds).stream()
                .collect(Collectors.toMap(
                        CatalogClient.VariantResponse::id,
                        Function.identity(),
                        (left, right) -> left));
        count.getLines().forEach(line -> {
            var variant = variants.get(line.getInventoryItem().getReferenceId());
            line.takeCostSnapshot(variant == null ? BigDecimal.ZERO : variant.costPrice());
        });
    }

    private boolean requiresApproval(InventoryCount count) {
        if (count.getLinesWithVariance().isEmpty()) return false;
        var policy = approvalPolicyRepository.findEffectivePolicies(count.getBranchId()).stream()
                .findFirst()
                .orElse(null);
        if (policy == null) return true;
        BigDecimal absoluteQuantity = count.getLinesWithVariance().stream()
                .map(line -> line.getVarianceQuantity().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal absoluteValue = count.getLinesWithVariance().stream()
                .map(line -> line.getVarianceValue().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return absoluteQuantity.compareTo(policy.getAutoApproveQuantityLimit()) > 0
                || absoluteValue.compareTo(policy.getAutoApproveValueLimit()) > 0;
    }

    private void ensureCounterCanUpdate(
            InventoryCount count,
            UUID userId,
            UpdateInventoryCountsRequest request) {
        List<InventoryCountAssignment> counters = count.getAssignments().stream()
                .filter(assignment -> assignment.getRole() == InventoryCountAssignmentRole.COUNTER)
                .toList();
        if (counters.isEmpty()) {
            return; // Compatibility for legacy counts created before assignment support.
        }
        InventoryCountAssignment assignment = counters.stream()
                .filter(item -> item.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Bạn không được phân công kiểm kê kỳ này"));
        if (assignment.getLocation() != null) {
            var requestedIds = request.lines().stream()
                    .map(UpdateInventoryCountsRequest.CountLineUpdate::lineId)
                    .collect(Collectors.toSet());
            boolean outsideLocation = count.getLines().stream()
                    .filter(line -> requestedIds.contains(line.getId()))
                    .anyMatch(line -> !line.getLocation().getId().equals(assignment.getLocation().getId()));
            if (outsideLocation) {
                throw new BadRequestException("Có dòng không thuộc vị trí được phân công");
            }
        }
        assignment.markInProgress();
        if (assignment.getStatus() == InventoryCountAssignmentStatus.COMPLETED) {
            assignment.setStatus(InventoryCountAssignmentStatus.IN_PROGRESS);
        }
    }

    private void ensureAssignedCounter(InventoryCount count, UUID userId) {
        List<InventoryCountAssignment> counters = count.getAssignments().stream()
                .filter(assignment -> assignment.getRole() == InventoryCountAssignmentRole.COUNTER)
                .toList();
        if (!counters.isEmpty() && counters.stream().noneMatch(item -> item.getUserId().equals(userId))) {
            throw new BadRequestException("Bạn không được phân công kiểm kê kỳ này");
        }
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(SecurityUtils.currentUserId());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("User ID hiện tại không phải UUID hợp lệ");
        }
    }

    private void requireAnyRole(String... roles) {
        for (String role : roles) {
            if (SecurityUtils.hasRole(role)) return;
        }
        throw new AccessDeniedException("Bạn không có quyền thực hiện thao tác kiểm kê này");
    }

    private void validateScopeInput(CreateInventoryTakeRequest request) {
        switch (request.scopeType()) {
            case LOCATION -> {
                if (request.locationIds() == null || request.locationIds().isEmpty()) {
                    throw new BadRequestException("locationIds bắt buộc khi scopeType = LOCATION");
                }
            }
            case PARTIAL, ITEM -> {
                if (request.inventoryItemIds() == null || request.inventoryItemIds().isEmpty()) {
                    throw new BadRequestException("inventoryItemIds bắt buộc khi scopeType = PARTIAL");
                }
            }
            case BATCH -> {
                if (request.batchIds() == null || request.batchIds().isEmpty()) {
                    throw new BadRequestException("batchIds bắt buộc khi scopeType = BATCH");
                }
            }
            case FULL, WAREHOUSE, MIXED -> { /* validated by the selected dimensions */ }
        }

        if (request.scopeType() == InventoryCountScopeType.LOCATION) {
            List<InventoryLocation> locations = locationRepository.findAllById(request.locationIds());
            if (locations.size() != request.locationIds().stream().distinct().count()) {
                throw new BadRequestException("Một hoặc nhiều vị trí kiểm kê không tồn tại");
            }
            boolean invalidLocation = locations.stream().anyMatch(location ->
                    !location.getBranchId().equals(request.branchId())
                            || location.getStatus() != InventoryStatus.ACTIVE);
            if (invalidLocation) {
                throw new BadRequestException("Vị trí kiểm kê phải hoạt động và thuộc chi nhánh đã chọn");
            }
        }

        if (request.scopeType() == InventoryCountScopeType.PARTIAL
                || request.scopeType() == InventoryCountScopeType.ITEM) {
            List<InventoryItem> items = itemRepository.findAllById(request.inventoryItemIds());
            if (items.size() != request.inventoryItemIds().stream().distinct().count()) {
                throw new BadRequestException("Một hoặc nhiều mặt hàng kiểm kê không tồn tại");
            }
            if (items.stream().anyMatch(item -> item.getStatus() != InventoryStatus.ACTIVE)) {
                throw new BadRequestException("Mặt hàng kiểm kê phải ở trạng thái hoạt động");
            }
        }
    }

    private InventoryCountType inferCountType(InventoryCountScopeType scopeType) {
        return switch (scopeType) {
            case FULL, WAREHOUSE -> InventoryCountType.FULL_COUNT;
            case PARTIAL, ITEM, MIXED -> InventoryCountType.CYCLE_COUNT;
            case LOCATION, BATCH -> InventoryCountType.SPOT_COUNT;
        };
    }

    private void ensureNoOverlappingOpenCount(InventoryCount candidate) {
        var openStatuses = EnumSet.of(
                InventoryCountStatus.FROZEN,
                InventoryCountStatus.COUNTING,
                InventoryCountStatus.RECONCILING,
                InventoryCountStatus.APPROVED,
                InventoryCountStatus.POSTED
        );
        boolean overlap = countRepository
                .findByBranchIdAndStatusInAndIsDeletedFalse(candidate.getBranchId(), openStatuses)
                .stream()
                .filter(existing -> !existing.getId().equals(candidate.getId()))
                .anyMatch(existing -> scopesOverlap(candidate, existing));
        if (overlap) {
            throw new BadRequestException("Đã có kỳ kiểm kê đang mở trùng phạm vi");
        }
    }

    private boolean scopesOverlap(InventoryCount left, InventoryCount right) {
        if (left.getWarehouseId() != null && right.getWarehouseId() != null
                && !left.getWarehouseId().equals(right.getWarehouseId())) {
            return false;
        }
        if (isWholeWarehouse(left) || isWholeWarehouse(right)) {
            return true;
        }
        return intersects(left.getScopeLocationIds(), right.getScopeLocationIds())
                || intersects(left.getScopeInventoryItemIds(), right.getScopeInventoryItemIds())
                || intersects(left.getScopeBatchIds(), right.getScopeBatchIds());
    }

    private boolean isWholeWarehouse(InventoryCount count) {
        return count.getScopeType() == InventoryCountScopeType.FULL
                || count.getScopeType() == InventoryCountScopeType.WAREHOUSE;
    }

    private boolean intersects(List<UUID> left, List<UUID> right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        var values = new HashSet<>(left);
        return right.stream().anyMatch(values::contains);
    }

    private List<InventoryBalance> queryBalancesForScope(InventoryCount count) {
        if (count.getScopeType() == InventoryCountScopeType.BATCH) {
            return List.of();
        }
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            root.fetch("inventoryItem");
            root.fetch("location");

            predicates.add(cb.equal(root.get("location").get("branchId"), count.getBranchId()));
            if (count.getWarehouseId() != null) {
                predicates.add(cb.equal(root.get("location").get("parentLocation").get("id"), count.getWarehouseId()));
            }
            if (count.getScopeType() == InventoryCountScopeType.LOCATION
                    || count.getScopeType() == InventoryCountScopeType.MIXED) {
                predicates.add(root.get("location").get("id").in(count.getScopeLocationIds()));
            }
            if (count.getScopeType() == InventoryCountScopeType.PARTIAL
                    || count.getScopeType() == InventoryCountScopeType.ITEM
                    || count.getScopeType() == InventoryCountScopeType.MIXED) {
                predicates.add(root.get("inventoryItem").get("id").in(count.getScopeInventoryItemIds()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return balanceRepository.findAll(spec);
    }

    private List<InventoryBatchBalance> queryBatchBalancesForScope(InventoryCount count) {
        return switch (count.getScopeType()) {
            case FULL, WAREHOUSE -> batchBalanceRepository
                    .findByBranchWithPositiveBalance(count.getBranchId());
            case LOCATION -> batchBalanceRepository
                    .findByLocationIdsWithPositiveBalance(count.getScopeLocationIds());
            case PARTIAL, ITEM -> batchBalanceRepository
                    .findByItemIdsAndBranchWithPositiveBalance(
                            count.getScopeInventoryItemIds(), count.getBranchId());
            case BATCH -> batchBalanceRepository
                    .findByBatchIdsAndBranchWithPositiveBalance(
                            count.getScopeBatchIds(), count.getBranchId());
            case MIXED -> batchBalanceRepository
                    .findByItemIdsAndBranchWithPositiveBalance(
                            count.getScopeInventoryItemIds(), count.getBranchId()).stream()
                    .filter(balance -> count.getScopeLocationIds().contains(balance.getLocation().getId()))
                    .toList();
        };
    }

    private String generateCountCode() {
        String code;
        do {
            code = "CT-" + LocalDate.now().format(CODE_DATE) + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (countRepository.existsByCountCode(code));
        return code;
    }
}
