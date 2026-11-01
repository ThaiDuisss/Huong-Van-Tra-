package org.hvtra.iventoryservice.replenishment.service;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.replenishment.repository.ShelfReplenishmentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.document.ShelfReplenishment;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.replenishment.dto.CreateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.RejectShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentLineRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentLineResponse;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentSummaryResponse;
import org.hvtra.iventoryservice.replenishment.dto.UpdateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ErrorCode;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.commonservice.common_core.service.BusinessSequenceService;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ShelfReplenishmentServiceImpl implements ShelfReplenishmentService {
    private static final String SEQUENCE_KEY = "NKE";
    private static final List<InventoryDocumentStatus> OPEN_STATUSES =
            List.of(InventoryDocumentStatus.DRAFT, InventoryDocumentStatus.PENDING_APPROVAL);
    /** reasonCode của phiếu do POS xuất kho ngay khi bán; manager duyệt sau. */
    static final String AUTO_ISSUE_REASON = "POS_AUTO_ISSUE";
    /** reasonCode của phiếu thu ngân tự nhập số lượng và đưa lên kệ ngay trên POS; manager duyệt sau. */
    static final String MANUAL_ISSUE_REASON = "POS_MANUAL_ISSUE";

    private final ShelfReplenishmentRepository replenishmentRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final BusinessSequenceService sequenceService;

    public ShelfReplenishmentServiceImpl(
            ShelfReplenishmentRepository replenishmentRepository,
            InventoryLocationRepository locationRepository,
            InventoryItemRepository itemRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            BusinessSequenceService sequenceService) {
        this.replenishmentRepository = replenishmentRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.sequenceService = sequenceService;
    }

    @Override
    public PageResponse<ShelfReplenishmentResponse> findAll(
            InventoryDocumentStatus status, UUID shelfId, String query, Boolean pendingReview, Pageable pageable) {
        Specification<ShelfReplenishment> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (Boolean.TRUE.equals(pendingReview)) {
                predicates.add(cb.equal(root.get("status"), InventoryDocumentStatus.POSTED));
                predicates.add(cb.isNull(root.get("approvedAt")));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (shelfId != null) {
                predicates.add(cb.equal(root.get("locationId"), shelfId));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("documentCode")), pattern),
                        cb.like(cb.lower(root.get("referenceId")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(replenishmentRepository.findAll(spec, pageable).map(document -> toResponse(document, false)));
    }

    @Override
    public ShelfReplenishmentSummaryResponse summary() {
        return new ShelfReplenishmentSummaryResponse(
                replenishmentRepository.countByStatusAndIsDeletedFalse(InventoryDocumentStatus.DRAFT),
                replenishmentRepository.countByStatusAndIsDeletedFalse(InventoryDocumentStatus.PENDING_APPROVAL),
                replenishmentRepository.countByStatusAndIsDeletedFalse(InventoryDocumentStatus.POSTED),
                replenishmentRepository.countByStatusAndIsDeletedFalse(InventoryDocumentStatus.CANCELLED),
                replenishmentRepository.countByStatusAndIsDeletedFalse(InventoryDocumentStatus.REJECTED),
                replenishmentRepository.countByStatusAndApprovedAtIsNullAndIsDeletedFalse(InventoryDocumentStatus.POSTED));
    }

    @Override
    public ShelfReplenishmentResponse findById(UUID id) {
        return toResponse(getDocument(id), true);
    }

    /**
     * Khi có {@code referenceId} (vd. mã đơn POS) và đã có phiếu còn mở cùng tham chiếu thì gộp vào phiếu đó:
     * mỗi mặt hàng chỉ một dòng, số lượng được đặt lại theo yêu cầu mới nhất. Nhờ vậy một đơn thiếu nhiều mặt
     * hàng chỉ sinh ra một phiếu nhập kệ.
     */
    @Override
    @Transactional
    public ShelfReplenishmentResponse create(CreateShelfReplenishmentRequest request) {
        Map<UUID, ShelfReplenishmentLineRequest> linesByVariant = mergeLines(request.lines());
        Map<UUID, InventoryItem> itemByVariant = resolveItems(linesByVariant.keySet());

        ShelfReplenishment existing = request.issueNow() ? null : findOpenByReference(request.referenceId());
        if (existing != null) {
            releaseAllocations(existing);
            upsertLines(existing, linesByVariant, itemByVariant);
            reserveLines(existing);
            if (request.submit() && existing.getStatus() == InventoryDocumentStatus.DRAFT) {
                existing.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
            }
            ShelfReplenishment saved = replenishmentRepository.save(existing);
            autoApproveIfCreatedByManager(saved);
            return toResponse(replenishmentRepository.save(saved), true);
        }

        InventoryLocation warehouse = resolveWarehouse(request.warehouseId(), request.shelfId(), request.branchId());
        InventoryLocation shelf = resolveShelf(request.shelfId(), warehouse, itemByVariant.values());

        ShelfReplenishment document = new ShelfReplenishment();
        document.setDocumentCode(sequenceService.nextCode(SEQUENCE_KEY, 5));
        document.setStatus(request.submit() ? InventoryDocumentStatus.PENDING_APPROVAL : InventoryDocumentStatus.DRAFT);
        document.setBranchId(request.branchId() != null ? request.branchId() : shelf.getBranchId());
        document.setLocationId(shelf.getId());
        document.setSourceLocation(warehouse);
        document.setDestinationLocation(shelf);
        document.setReferenceType(InventoryReferenceType.STOCK_TRANSFER);
        document.setReferenceId(blankToNull(request.referenceId()));
        document.setNote(blankToNull(request.note()));
        upsertLines(document, linesByVariant, itemByVariant);
        if (request.issueNow()) {
            // Thu ngân tự chọn số lượng đưa lên kệ: chuyển hàng ngay (kho thiếu thì báo lỗi), manager duyệt lại sau.
            document.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
            document.setReasonCode(MANUAL_ISSUE_REASON);
            ShelfReplenishment saved = replenishmentRepository.save(document);
            issue(saved);
            return toResponse(replenishmentRepository.save(saved), true);
        }
        reserveLines(document);
        // Lưu trước để có id (giao dịch kho tham chiếu id phiếu) và createdBy do auditing gán
        ShelfReplenishment saved = replenishmentRepository.save(document);
        autoApproveIfCreatedByManager(saved);
        return toResponse(replenishmentRepository.save(saved), true);
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse update(UUID id, UpdateShelfReplenishmentRequest request) {
        ShelfReplenishment document = getDocument(id);
        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Chỉ sửa được phiếu nhập kệ đang ở trạng thái nháp");
        }
        releaseAllocations(document);

        Map<UUID, ShelfReplenishmentLineRequest> linesByVariant = mergeLines(request.lines());
        Map<UUID, InventoryItem> itemByVariant = resolveItems(linesByVariant.keySet());

        InventoryLocation warehouse = request.warehouseId() != null || request.shelfId() != null
                ? resolveWarehouse(request.warehouseId(), request.shelfId(), document.getBranchId())
                : document.getSourceLocation();
        InventoryLocation shelf = request.shelfId() != null
                ? resolveShelf(request.shelfId(), warehouse, itemByVariant.values())
                : document.getDestinationLocation();
        document.setSourceLocation(warehouse);
        document.setDestinationLocation(shelf);
        document.setLocationId(shelf.getId());
        document.setNote(blankToNull(request.note()));

        // Xóa hết dòng cũ và flush trước khi thêm lại để không đụng unique (document_id, line_no).
        document.getLines().clear();
        replenishmentRepository.saveAndFlush(document);
        upsertLines(document, linesByVariant, itemByVariant);
        reserveLines(document);
        return toResponse(replenishmentRepository.save(document), true);
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse submit(UUID id) {
        ShelfReplenishment document = getDocument(id);
        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Chỉ gửi được phiếu nhập kệ đang ở trạng thái nháp");
        }
        if (document.getLines().isEmpty()) {
            throw new BadRequestException("Phiếu nhập kệ chưa có mặt hàng nào");
        }
        boolean hasAllocations = document.getLines().stream()
                .anyMatch(line -> line.getBatchAllocations() != null && !line.getBatchAllocations().isEmpty());
        if (!hasAllocations) {
            reserveLines(document);
        }
        document.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
        autoApproveIfCreatedByManager(document);
        return toResponse(replenishmentRepository.save(document), true);
    }

    /**
     * Quản lý / Admin tự tạo và gửi phiếu thì không cần người khác duyệt: chuyển hàng lên kệ ngay. Kho không đủ
     * hàng thì giữ phiếu ở trạng thái chờ duyệt (không báo lỗi) để bổ sung kho rồi duyệt sau.
     */
    private void autoApproveIfCreatedByManager(ShelfReplenishment document) {
        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL || !isManagerOrAdmin()) {
            return;
        }
        UUID currentUserId = currentUserId();
        if (currentUserId == null || !currentUserId.equals(document.getCreatedBy())) {
            return;
        }
        boolean allReserved = !document.getLines().isEmpty() && document.getLines().stream()
                .allMatch(line -> line.getBatchAllocations() != null && !line.getBatchAllocations().isEmpty());
        if (!allReserved && !warehouseHasEnough(document)) {
            return;
        }
        issue(document);
        document.setApprovedBy(currentUserId);
        document.setApprovedAt(Instant.now());
    }

    private static boolean isManagerOrAdmin() {
        return SecurityUtils.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER);
    }

    /** Một query gom tồn khả dụng của các mặt hàng trong phiếu tại kho nguồn; issue() vẫn kiểm tra lại khi chuyển. */
    private boolean warehouseHasEnough(ShelfReplenishment document) {
        InventoryLocation warehouse = document.getSourceLocation();
        if (warehouse == null || document.getLines().isEmpty()) {
            return false;
        }
        Map<UUID, BigDecimal> needed = new HashMap<>();
        for (InventoryDocumentLine line : document.getLines()) {
            needed.merge(line.getInventoryItem().getId(), line.getOrderedQuantity(), BigDecimal::add);
        }
        Map<UUID, BigDecimal> available = new HashMap<>();
        for (Object[] row : balanceRepository.sumAvailableByItemsAndLocations(
                List.copyOf(needed.keySet()), List.of(warehouse.getId()))) {
            available.put((UUID) row[0], (BigDecimal) row[2]);
        }
        return needed.entrySet().stream()
                .allMatch(entry -> available.getOrDefault(entry.getKey(), BigDecimal.ZERO).compareTo(entry.getValue()) >= 0);
    }

    /**
     * Duyệt phiếu (SC-05): người duyệt phải khác người tạo; kiểm tra kho đủ hàng rồi trừ kho theo FEFO (batch hết
     * hạn sớm nhất trước) và cộng đúng batch đó vào kệ, ghi giao dịch kho. Thiếu hàng ở bất kỳ dòng nào thì cả phiếu
     * không được ghi (rollback), phiếu vẫn chờ duyệt.
     */
    @Override
    @Transactional
    public ShelfReplenishmentResponse approve(UUID id) {
        ShelfReplenishment document = getPendingForDecision(id);
        issue(document);
        document.setApprovedBy(currentUserId());
        document.setApprovedAt(Instant.now());
        return toResponse(replenishmentRepository.save(document), true);
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse reject(UUID id, RejectShelfReplenishmentRequest request) {
        ShelfReplenishment document = getPendingForDecision(id);
        releaseAllocations(document);
        document.setStatus(InventoryDocumentStatus.REJECTED);
        document.setRejectedBy(currentUserId());
        document.setRejectedAt(Instant.now());
        document.setRejectionReason(request.reason().trim());
        return toResponse(replenishmentRepository.save(document), true);
    }

    /** Phiếu đang chờ duyệt và người thao tác không phải người tạo (SC-05 EX-02). */
    private ShelfReplenishment getPendingForDecision(UUID id) {
        ShelfReplenishment document = getDocument(id);
        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL) {
            throw ErrorCode.SHELF_REPLENISHMENT_NOT_PENDING.exception();
        }
        UUID currentUserId = currentUserId();
        if (currentUserId != null && currentUserId.equals(document.getCreatedBy())) {
            throw ErrorCode.SHELF_REPLENISHMENT_SELF_APPROVAL.exception();
        }
        return document;
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse review(UUID id) {
        ShelfReplenishment document = getDocument(id);
        if (document.getStatus() != InventoryDocumentStatus.POSTED) {
            throw new BadRequestException("Chỉ duyệt được phiếu đã xuất hàng lên kệ");
        }
        if (document.getApprovedAt() == null) {
            document.setApprovedBy(currentUserId());
            document.setApprovedAt(Instant.now());
        }
        return toResponse(replenishmentRepository.save(document), true);
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse autoIssueForOrder(UUID orderId, UUID branchId,
            Map<UUID, BigDecimal> shortageByVariantId) {
        ShelfReplenishment existing = findOpenByReference(orderId.toString());
        Map<UUID, ShelfReplenishmentLineRequest> lines = new LinkedHashMap<>();
        shortageByVariantId.forEach((variantId, shortage) -> {
            if (shortage != null && shortage.compareTo(BigDecimal.ZERO) > 0) {
                lines.put(variantId, new ShelfReplenishmentLineRequest(variantId, shortage, null));
            }
        });

        if (lines.isEmpty()) {
            if (existing != null) {
                releaseAllocations(existing);
                existing.setStatus(InventoryDocumentStatus.CANCELLED);
                String current = existing.getNote();
                existing.setNote((current == null || current.isBlank() ? "" : current + "\n")
                        + "Hủy: kệ đã đủ hàng khi thanh toán đơn");
                replenishmentRepository.save(existing);
            }
            return null;
        }

        Map<UUID, InventoryItem> itemByVariant = resolveItems(lines.keySet());
        ShelfReplenishment document = existing;
        if (document == null) {
            InventoryLocation warehouse = resolveWarehouse(null, null, branchId);
            InventoryLocation shelf = resolveShelf(null, warehouse, itemByVariant.values());
            document = new ShelfReplenishment();
            document.setDocumentCode(sequenceService.nextCode(SEQUENCE_KEY, 5));
            document.setBranchId(branchId != null ? branchId : shelf.getBranchId());
            document.setLocationId(shelf.getId());
            document.setSourceLocation(warehouse);
            document.setDestinationLocation(shelf);
            document.setReferenceType(InventoryReferenceType.STOCK_TRANSFER);
            document.setReferenceId(orderId.toString());
            document.setNote("POS: xuất kho lên kệ để bán ngay cho đơn " + orderId);
        } else {
            releaseAllocations(document);
            // Chỉ giữ các mặt hàng thực sự thiếu lúc thanh toán; số lượng lấy theo phần thiếu thực tế.
            document.getLines().removeIf(line -> !lines.containsKey(line.getInventoryItem().getReferenceId()));
        }
        document.setReasonCode(AUTO_ISSUE_REASON);
        upsertLines(document, lines, itemByVariant);
        document = replenishmentRepository.save(document);
        issue(document);
        return toResponse(replenishmentRepository.save(document), true);
    }

    /** Chuyển hàng của phiếu từ kho lên kệ và đánh dấu POSTED. Việc duyệt (approved*) do nơi gọi quyết định. */
    private void issue(InventoryDocument document) {
        if (document.getLines().isEmpty()) {
            throw new BadRequestException("Phiếu nhập kệ chưa có mặt hàng nào");
        }
        InventoryLocation warehouse = document.getSourceLocation();
        InventoryLocation shelf = document.getDestinationLocation();
        if (warehouse == null || shelf == null) {
            throw new BadRequestException("Phiếu nhập kệ thiếu kho nguồn hoặc kệ đích");
        }

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode("TRF-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setType(InventoryTransactionType.TRANSFER);
        transaction.setReferenceType(InventoryReferenceType.STOCK_TRANSFER);
        transaction.setReferenceId(document.getId().toString());
        transaction.setBranchId(document.getBranchId() != null ? document.getBranchId() : shelf.getBranchId());
        transaction.setInventoryDocument(document);

        for (InventoryDocumentLine line : document.getLines()) {
            moveLine(line, warehouse, shelf, transaction);
        }
        transactionRepository.save(transaction);

        document.setStatus(InventoryDocumentStatus.POSTED);
        document.setPostedBy(currentUserId());
        document.setPostedAt(Instant.now());
        document.setReceivedBy(currentUserId());
        document.setReceivedAt(Instant.now());
    }

    @Override
    @Transactional
    public ShelfReplenishmentResponse cancel(UUID id, String note) {
        ShelfReplenishment document = getDocument(id);
        if (document.getStatus() == InventoryDocumentStatus.CANCELLED) {
            return toResponse(document, true);
        }
        if (!OPEN_STATUSES.contains(document.getStatus())) {
            throw ErrorCode.SHELF_REPLENISHMENT_NOT_CANCELLABLE.exception();
        }
        // SC-05 EX-05: người tạo hoặc Admin mới được hủy phiếu chưa duyệt
        UUID currentUserId = currentUserId();
        boolean isCreator = currentUserId != null && currentUserId.equals(document.getCreatedBy());
        if (!isCreator && !SecurityUtils.isSuperAdmin()) {
            throw ErrorCode.SHELF_REPLENISHMENT_CANCEL_FORBIDDEN.exception();
        }
        releaseAllocations(document);
        document.setStatus(InventoryDocumentStatus.CANCELLED);
        if (note != null && !note.isBlank()) {
            String current = document.getNote();
            document.setNote((current == null || current.isBlank() ? "" : current + "\n") + "Hủy: " + note.trim());
        }
        return toResponse(replenishmentRepository.save(document), true);
    }

    // ---------------------------------------------------------------- stock movement

    private void moveLine(InventoryDocumentLine line, InventoryLocation warehouse, InventoryLocation shelf,
            InventoryTransaction transaction) {
        InventoryItem item = line.getInventoryItem();
        BigDecimal remaining = line.getOrderedQuantity();

        if (line.getBatchAllocations() != null && !line.getBatchAllocations().isEmpty()) {
            for (InventoryDocumentBatchAllocation allocation : line.getBatchAllocations()) {
                BigDecimal take = allocation.getQuantity();
                if (take == null || take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                InventoryBatch batch = allocation.getInventoryBatch();

                InventoryBalance source = balanceRepository.findForUpdateByBatchId(batch.getId(), warehouse.getId())
                        .orElseThrow(() -> new BadRequestException("Không tìm thấy tồn kho cho lô " + batch.getBatchCode()));

                BigDecimal sourceBefore = source.getQuantityOnHand();
                BigDecimal sourceAfter = sourceBefore.subtract(take);
                source.setQuantityOnHand(sourceAfter);
                BigDecimal newReserved = source.getQuantityReserved().subtract(take);
                source.setQuantityReserved(newReserved.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newReserved);
                balanceRepository.save(source);
                transaction.addLine(transactionLine(item, warehouse, batch, take.negate(), sourceBefore, sourceAfter));

                InventoryBalance target = balanceRepository.findForUpdateByBatchId(batch.getId(), shelf.getId())
                        .orElseGet(() -> {
                            InventoryBalance balance = new InventoryBalance();
                            balance.setInventoryItem(item);
                            balance.setInventoryBatch(batch);
                            balance.setLocation(shelf);
                            balance.setQuantityOnHand(BigDecimal.ZERO);
                            balance.setQuantityReserved(BigDecimal.ZERO);
                            balance.setQuantityBlocked(BigDecimal.ZERO);
                            return balance;
                        });
                BigDecimal targetBefore = target.getQuantityOnHand();
                BigDecimal targetAfter = targetBefore.add(take);
                target.setQuantityOnHand(targetAfter);
                balanceRepository.save(target);
                transaction.addLine(transactionLine(item, shelf, batch, take, targetBefore, targetAfter));
            }
        } else {
            List<InventoryBalance> sources = balanceRepository.findFefoAvailableForUpdate(
                    item.getId(), warehouse.getId(), InventoryStatus.ACTIVE, LocalDate.now());

            BigDecimal available = sources.stream()
                    .map(InventoryBalance::getAvailableQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(remaining) < 0) {
                throw new BadRequestException("Kho " + warehouse.getName() + " không đủ hàng cho " + item.getName()
                        + ": cần " + remaining.stripTrailingZeros().toPlainString()
                        + ", còn " + available.stripTrailingZeros().toPlainString());
            }

            for (InventoryBalance source : sources) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = source.getAvailableQuantity().min(remaining);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                InventoryBatch batch = source.getInventoryBatch();

                BigDecimal sourceBefore = source.getQuantityOnHand();
                BigDecimal sourceAfter = sourceBefore.subtract(take);
                source.setQuantityOnHand(sourceAfter);
                balanceRepository.save(source);
                transaction.addLine(transactionLine(item, warehouse, batch, take.negate(), sourceBefore, sourceAfter));

                InventoryBalance target = balanceRepository.findForUpdateByBatchId(batch.getId(), shelf.getId())
                        .orElseGet(() -> {
                            InventoryBalance balance = new InventoryBalance();
                            balance.setInventoryItem(item);
                            balance.setInventoryBatch(batch);
                            balance.setLocation(shelf);
                            balance.setQuantityOnHand(BigDecimal.ZERO);
                            balance.setQuantityReserved(BigDecimal.ZERO);
                            balance.setQuantityBlocked(BigDecimal.ZERO);
                            return balance;
                        });
                BigDecimal targetBefore = target.getQuantityOnHand();
                BigDecimal targetAfter = targetBefore.add(take);
                target.setQuantityOnHand(targetAfter);
                balanceRepository.save(target);
                transaction.addLine(transactionLine(item, shelf, batch, take, targetBefore, targetAfter));

                remaining = remaining.subtract(take);
            }
        }

        line.setReceivedQuantity(line.getOrderedQuantity());
        line.setAcceptedQuantity(line.getOrderedQuantity());
    }

    // ---------------------------------------------------------------- reservations

    private void reserveLines(ShelfReplenishment document) {
        InventoryLocation warehouse = document.getSourceLocation();
        if (warehouse == null) {
            throw new BadRequestException("Phiếu nhập kệ thiếu kho nguồn");
        }
        for (InventoryDocumentLine line : document.getLines()) {
            if (line.getBatchAllocations() != null && !line.getBatchAllocations().isEmpty()) {
                releaseAllocationsForLine(line, warehouse);
            }
            InventoryItem item = line.getInventoryItem();
            BigDecimal remaining = line.getOrderedQuantity();
            if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<InventoryBalance> sources = balanceRepository.findFefoAvailableForUpdate(
                    item.getId(), warehouse.getId(), InventoryStatus.ACTIVE, LocalDate.now());

            BigDecimal available = sources.stream()
                    .map(InventoryBalance::getAvailableQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(remaining) < 0) {
                throw new BadRequestException("Kho " + warehouse.getName() + " không đủ hàng cho " + item.getName()
                        + ": cần " + remaining.stripTrailingZeros().toPlainString()
                        + ", còn " + available.stripTrailingZeros().toPlainString());
            }

            for (InventoryBalance source : sources) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal take = source.getAvailableQuantity().min(remaining);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                InventoryBatch batch = source.getInventoryBatch();

                InventoryDocumentBatchAllocation allocation = new InventoryDocumentBatchAllocation();
                allocation.setInventoryBatch(batch);
                allocation.setQuantity(take);
                line.addBatchAllocation(allocation);

                source.setQuantityReserved(source.getQuantityReserved().add(take));
                balanceRepository.save(source);

                remaining = remaining.subtract(take);
            }
        }
    }

    private void releaseAllocations(ShelfReplenishment document) {
        InventoryLocation warehouse = document.getSourceLocation();
        if (warehouse == null) {
            return;
        }
        for (InventoryDocumentLine line : document.getLines()) {
            releaseAllocationsForLine(line, warehouse);
        }
    }

    private void releaseAllocationsForLine(InventoryDocumentLine line, InventoryLocation warehouse) {
        if (line.getBatchAllocations() == null || line.getBatchAllocations().isEmpty()) {
            return;
        }
        for (InventoryDocumentBatchAllocation allocation : line.getBatchAllocations()) {
            BigDecimal qty = allocation.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            balanceRepository.findForUpdateByBatchId(allocation.getInventoryBatch().getId(), warehouse.getId())
                    .ifPresent(source -> {
                        BigDecimal newReserved = source.getQuantityReserved().subtract(qty);
                        if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
                            newReserved = BigDecimal.ZERO;
                        }
                        source.setQuantityReserved(newReserved);
                        balanceRepository.save(source);
                    });
        }
        line.getBatchAllocations().clear();
    }

    private InventoryTransactionLine transactionLine(InventoryItem item, InventoryLocation location,
            InventoryBatch batch, BigDecimal quantity, BigDecimal before, BigDecimal after) {
        InventoryTransactionLine line = new InventoryTransactionLine();
        line.setInventoryItem(item);
        line.setLocation(location);
        line.setQuantity(quantity);
        line.setBeforeQuantity(before);
        line.setAfterQuantity(after);

        InventoryTransactionBatchAllocation allocation = new InventoryTransactionBatchAllocation();
        allocation.setInventoryBatch(batch);
        allocation.setQuantity(quantity);
        allocation.setBeforeBatchQuantity(before);
        allocation.setAfterBatchQuantity(after);
        line.addBatchAllocation(allocation);
        return line;
    }

    // ---------------------------------------------------------------- lines

    /** Gộp các dòng trùng mặt hàng trong cùng một request. */
    private Map<UUID, ShelfReplenishmentLineRequest> mergeLines(List<ShelfReplenishmentLineRequest> lines) {
        Map<UUID, ShelfReplenishmentLineRequest> merged = new LinkedHashMap<>();
        for (ShelfReplenishmentLineRequest line : lines) {
            merged.merge(line.productVariantId(), line, (left, right) -> new ShelfReplenishmentLineRequest(
                    left.productVariantId(), left.quantity().add(right.quantity()),
                    right.note() != null ? right.note() : left.note()));
        }
        return merged;
    }

    private Map<UUID, InventoryItem> resolveItems(Set<UUID> variantIds) {
        Map<UUID, InventoryItem> itemByVariant = new HashMap<>();
        for (InventoryItem item : itemRepository.findByReferenceIdInAndItemType(variantIds, InventoryItemType.PRODUCT_VARIANT)) {
            if (item.getStatus() == InventoryStatus.ACTIVE) {
                itemByVariant.putIfAbsent(item.getReferenceId(), item);
            }
        }
        for (UUID variantId : variantIds) {
            if (!itemByVariant.containsKey(variantId)) {
                throw new BadRequestException("Mặt hàng " + variantId + " chưa được khai báo trong kho");
            }
        }
        return itemByVariant;
    }

    private void upsertLines(InventoryDocument document, Map<UUID, ShelfReplenishmentLineRequest> linesByVariant,
            Map<UUID, InventoryItem> itemByVariant) {
        int nextLineNo = document.getLines().stream()
                .map(InventoryDocumentLine::getLineNo)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        for (ShelfReplenishmentLineRequest request : linesByVariant.values()) {
            InventoryItem item = itemByVariant.get(request.productVariantId());
            InventoryDocumentLine line = document.getLines().stream()
                    .filter(existing -> existing.getInventoryItem().getId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (line == null) {
                line = new InventoryDocumentLine();
                line.setLineNo(nextLineNo++);
                line.setInventoryItem(item);
                line.setUnitId(item.getUnitId());
                document.addLine(line);
            }
            line.setSourceLocation(document.getSourceLocation());
            line.setDestinationLocation(document.getDestinationLocation());
            line.setOrderedQuantity(request.quantity());
            if (request.note() != null && !request.note().isBlank()) {
                line.setNote(request.note().trim());
            }
        }
    }

    private ShelfReplenishment findOpenByReference(String referenceId) {
        String reference = blankToNull(referenceId);
        if (reference == null) {
            return null;
        }
        return replenishmentRepository
                .findFirstByReferenceIdAndStatusInAndIsDeletedFalseOrderByCreatedAtDesc(reference, OPEN_STATUSES)
                .orElse(null);
    }

    // ---------------------------------------------------------------- locations

    private InventoryLocation resolveWarehouse(UUID warehouseId, UUID shelfId, UUID branchId) {
        if (warehouseId != null) {
            InventoryLocation warehouse = locationRepository.findById(warehouseId)
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", warehouseId));
            if (warehouse.getType() == InventoryLocationType.SHELF || warehouse.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("Kho nguồn không hợp lệ");
            }
            return warehouse;
        }
        if (shelfId != null) {
            InventoryLocation shelf = locationRepository.findById(shelfId)
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", shelfId));
            if (shelf.getParentLocation() != null) {
                return shelf.getParentLocation();
            }
        }
        if (branchId != null) {
            var byBranch = locationRepository.findFirstByBranchIdAndTypeAndStatus(
                    branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
            if (byBranch.isPresent()) {
                return byBranch.get();
            }
        }
        return locationRepository
                .findFirstByTypeAndStatusOrderByCreatedAtAsc(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Chưa có kho nào đang hoạt động"));
    }

    /** Không chỉ định kệ thì ưu tiên kệ đang bày mặt hàng đầu tiên của phiếu, sau đó là kệ đầu tiên của kho. */
    private InventoryLocation resolveShelf(UUID shelfId, InventoryLocation warehouse, Iterable<InventoryItem> items) {
        if (shelfId != null) {
            InventoryLocation shelf = locationRepository.findById(shelfId)
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", shelfId));
            if (shelf.getType() != InventoryLocationType.SHELF || shelf.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("Kệ đích không hợp lệ");
            }
            return shelf;
        }
        List<InventoryLocation> shelves = locationRepository
                .findByParentLocation_IdAndTypeAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                        warehouse.getId(), InventoryLocationType.SHELF, InventoryStatus.ACTIVE);
        if (shelves.isEmpty()) {
            throw new BadRequestException("Kho " + warehouse.getName() + " chưa có kệ nào đang hoạt động");
        }
        List<UUID> itemIds = new ArrayList<>();
        items.forEach(item -> itemIds.add(item.getId()));
        Map<UUID, BigDecimal> stockByShelf = new HashMap<>();
        if (!itemIds.isEmpty()) {
            UUID firstItemId = itemIds.getFirst();
            for (Object[] row : balanceRepository.sumAvailableByItemsAndLocations(
                    List.of(firstItemId), shelves.stream().map(InventoryLocation::getId).toList())) {
                stockByShelf.put((UUID) row[1], (BigDecimal) row[2]);
            }
        }
        return shelves.stream()
                .filter(shelf -> stockByShelf.getOrDefault(shelf.getId(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                .max((left, right) -> stockByShelf.get(left.getId()).compareTo(stockByShelf.get(right.getId())))
                .orElse(shelves.getFirst());
    }

    // ---------------------------------------------------------------- mapping

    private ShelfReplenishmentResponse toResponse(InventoryDocument document, boolean withLines) {
        InventoryLocation warehouse = document.getSourceLocation();
        InventoryLocation shelf = document.getDestinationLocation();
        List<InventoryDocumentLine> lines = document.getLines();
        BigDecimal total = lines.stream()
                .map(InventoryDocumentLine::getOrderedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ShelfReplenishmentLineResponse> lineResponses = List.of();
        if (withLines && !lines.isEmpty()) {
            Map<String, BigDecimal> stock = new HashMap<>();
            if (warehouse != null && shelf != null) {
                for (Object[] row : balanceRepository.sumAvailableByItemsAndLocations(
                        lines.stream().map(line -> line.getInventoryItem().getId()).toList(),
                        List.of(warehouse.getId(), shelf.getId()))) {
                    stock.put(row[0] + ":" + row[1], (BigDecimal) row[2]);
                }
            }
            lineResponses = lines.stream()
                    .sorted((left, right) -> left.getLineNo().compareTo(right.getLineNo()))
                    .map(line -> {
                        InventoryItem item = line.getInventoryItem();
                        return new ShelfReplenishmentLineResponse(
                                line.getId(),
                                line.getLineNo(),
                                item.getId(),
                                item.getReferenceId(),
                                item.getReferenceCode(),
                                item.getName(),
                                line.getUnitId(),
                                line.getOrderedQuantity(),
                                line.getReceivedQuantity(),
                                warehouse == null ? BigDecimal.ZERO
                                        : stock.getOrDefault(item.getId() + ":" + warehouse.getId(), BigDecimal.ZERO),
                                shelf == null ? BigDecimal.ZERO
                                        : stock.getOrDefault(item.getId() + ":" + shelf.getId(), BigDecimal.ZERO),
                                line.getNote());
                    })
                    .toList();
        }

        return new ShelfReplenishmentResponse(
                document.getId(),
                document.getDocumentCode(),
                document.getStatus(),
                document.getBranchId(),
                warehouse != null ? warehouse.getId() : null,
                warehouse != null ? warehouse.getName() : null,
                shelf != null ? shelf.getId() : null,
                shelf != null ? shelf.getName() : null,
                document.getReferenceId(),
                document.getNote(),
                total,
                lines.size(),
                document.getCreatedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getPostedBy(),
                document.getPostedAt(),
                AUTO_ISSUE_REASON.equals(document.getReasonCode())
                        || MANUAL_ISSUE_REASON.equals(document.getReasonCode()),
                document.getStatus() == InventoryDocumentStatus.POSTED && document.getApprovedAt() == null,
                document.getApprovedBy(),
                document.getApprovedAt(),
                lineResponses,
                document.getRejectedBy(),
                document.getRejectedAt(),
                document.getRejectionReason());
    }

    // ---------------------------------------------------------------- helpers

    private ShelfReplenishment getDocument(UUID id) {
        return replenishmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShelfReplenishment", id));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InternalUserPrincipal principal) {
            try {
                return UUID.fromString(principal.userId());
            } catch (IllegalArgumentException | NullPointerException ignored) {
                return null;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
