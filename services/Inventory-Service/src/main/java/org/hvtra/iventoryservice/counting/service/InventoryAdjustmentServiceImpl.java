package org.hvtra.iventoryservice.counting.service;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.counting.mapper.AdjustmentMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryAdjustmentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Transactional(readOnly = true)
public class InventoryAdjustmentServiceImpl implements InventoryAdjustmentService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final AdjustmentMapper mapper;

    public InventoryAdjustmentServiceImpl(
            InventoryAdjustmentRepository adjustmentRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryTransactionRepository transactionRepository,
            AdjustmentMapper mapper) {
        this.adjustmentRepository = adjustmentRepository;
        this.balanceRepository = balanceRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.transactionRepository = transactionRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<AdjustmentResponse> findAll(
            UUID branchId, InventoryDocumentStatus status, String keyword, Pageable pageable) {
        Specification<InventoryAdjustment> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("documentCode")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(
                adjustmentRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public AdjustmentResponse findById(UUID id) {
        InventoryAdjustment document = findDocumentOrThrow(id);
        return mapper.toResponse(document);
    }

    @Override
    @Transactional
    public AdjustmentResponse create(CreateAdjustmentRequest request) {
        requireAnyRole(Role.SUPER_ADMIN, Role.MANAGER, Role.INVENTORY_STAFF);
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("Danh sách dòng điều chỉnh không được rỗng");
        }

        List<InventoryDocumentLine> lines = new ArrayList<>();
        AtomicInteger lineNo = new AtomicInteger(1);
        for (CreateAdjustmentRequest.AdjustmentLineRequest lineRequest : request.lines()) {
            InventoryItem item = itemRepository.findById(lineRequest.inventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item", lineRequest.inventoryItemId()));
            if (item.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("Mặt hàng không hoạt động: " + item.getId());
            }

            InventoryLocation location = locationRepository.findById(lineRequest.locationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory location", lineRequest.locationId()));
            if (location.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("Vị trí không hoạt động: " + location.getId());
            }

            InventoryDocumentLine docLine = new InventoryDocumentLine();
            docLine.setLineNo(lineNo.getAndIncrement());
            docLine.setInventoryItem(item);
            docLine.setSourceLocation(location);
            docLine.setUnitId(item.getUnitId());
            docLine.setOrderedQuantity(lineRequest.adjustmentQuantity());
            docLine.setNote(lineRequest.note());
            lines.add(docLine);
        }

        InventoryAdjustment document = newDocument(lines);
        document.setBranchId(request.branchId());
        document.setReasonCode(request.reasonCode());
        document.setNote(request.note());
        document.setReferenceType(InventoryReferenceType.MANUAL);
        return mapper.toResponse(adjustmentRepository.save(document));
    }

    /**
     * Tạo phiếu điều chỉnh từ các dòng chênh lệch của kỳ kiểm kê, duyệt và ghi sổ ngay.
     * Phải chạy trong transaction của thao tác approve kiểm kê: nếu ghi sổ lỗi thì rollback cả kỳ kiểm kê.
     */
    @Override
    @Transactional
    public InventoryAdjustment createAndPostFromCount(InventoryCount count, UUID approvedBy) {
        List<InventoryCountLine> varianceLines = count.getLinesWithVariance();
        if (varianceLines.isEmpty()) {
            throw new BadRequestException("Không có chênh lệch cần điều chỉnh");
        }

        List<InventoryDocumentLine> lines = new ArrayList<>();
        AtomicInteger lineNo = new AtomicInteger(1);
        for (InventoryCountLine countLine : varianceLines) {
            InventoryDocumentLine docLine = new InventoryDocumentLine();
            docLine.setLineNo(lineNo.getAndIncrement());
            docLine.setInventoryItem(countLine.getInventoryItem());
            docLine.setSourceLocation(countLine.getLocation());
            docLine.setUnitId(countLine.getInventoryItem().getUnitId());
            docLine.setOrderedQuantity(countLine.getVarianceQuantity());
            if (countLine.getInventoryBatch() != null) {
                InventoryDocumentBatchAllocation allocation = new InventoryDocumentBatchAllocation();
                allocation.setInventoryBatch(countLine.getInventoryBatch());
                allocation.setQuantity(countLine.getVarianceQuantity());
                docLine.addBatchAllocation(allocation);
            }
            lines.add(docLine);
        }

        InventoryAdjustment document = newDocument(lines);
        document.setBranchId(count.getBranchId());
        document.setReasonCode(InventoryReferenceType.STOCK_COUNT.name());
        document.setNote("Điều chỉnh từ kỳ kiểm kê " + count.getCountCode());
        document.setReferenceType(InventoryReferenceType.STOCK_COUNT);
        document.setReferenceId(count.getId().toString());
        document.approve(approvedBy);

        InventoryAdjustment saved = adjustmentRepository.save(document);
        applyToStock(saved);
        saved.post();
        return adjustmentRepository.save(saved);
    }

    @Override
    @Transactional
    public AdjustmentResponse approve(UUID id) {
        requireAnyRole(Role.SUPER_ADMIN, Role.MANAGER);
        InventoryAdjustment document = findDocumentOrThrow(id);

        // Delegate to entity
        document.approve(currentUserId());

        return mapper.toResponse(adjustmentRepository.save(document));
    }

    @Override
    @Transactional
    public AdjustmentResponse post(UUID id) {
        requireAnyRole(Role.SUPER_ADMIN, Role.MANAGER, Role.INVENTORY_STAFF);
        InventoryAdjustment document = findDocumentOrThrow(id);

        // Delegate validation to entity
        document.validateCanPost();

        applyToStock(document);
        document.post();

        return mapper.toResponse(adjustmentRepository.save(document));
    }

    @Override
    @Transactional
    public AdjustmentResponse reject(UUID id) {
        requireAnyRole(Role.SUPER_ADMIN, Role.MANAGER);
        InventoryAdjustment document = findDocumentOrThrow(id);

        // Delegate to entity
        document.reject();

        return mapper.toResponse(adjustmentRepository.save(document));
    }

    // ====== Private Helpers ======

    private InventoryAdjustment newDocument(List<InventoryDocumentLine> lines) {
        // Loại phiếu (tăng / giảm) phụ thuộc tổng điều chỉnh
        BigDecimal netQuantity = lines.stream()
                .map(InventoryDocumentLine::getOrderedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        InventoryAdjustment document = InventoryAdjustment.forNetQuantity(netQuantity);
        document.setDocumentCode(generateDocumentCode());
        lines.forEach(document::addLine);
        return document;
    }

    /**
     * Cập nhật tồn theo các dòng của phiếu và ghi transaction. Lỗi nếu tồn sau điều chỉnh âm.
     */
    private void applyToStock(InventoryAdjustment document) {
        // Orchestration: update balances and create transaction
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(document.isIncrease()
                ? InventoryTransactionType.ADJUSTMENT_IN
                : InventoryTransactionType.ADJUSTMENT_OUT);
        transaction.setBranchId(document.getBranchId());
        transaction.setReferenceType(InventoryReferenceType.INVENTORY_DOCUMENT);
        transaction.setReferenceId(document.getId().toString());

        for (InventoryDocumentLine docLine : document.getLines()) {
            InventoryBalance balance = balanceRepository
                    .findForUpdate(docLine.getInventoryItem().getId(),
                            docLine.getSourceLocation().getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Không tìm thấy tồn kho cho mặt hàng " + docLine.getInventoryItem().getId()
                                    + " tại vị trí " + docLine.getSourceLocation().getId()));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = before.add(docLine.getOrderedQuantity());

            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException(
                        "Tồn kho không đủ để điều chỉnh giảm cho mặt hàng " + docLine.getInventoryItem().getId());
            }

            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);

            // Delegate to entity
            docLine.markProcessed();

            // Create transaction line
            InventoryTransactionLine txLine = new InventoryTransactionLine();
            txLine.setInventoryItem(docLine.getInventoryItem());
            txLine.setLocation(docLine.getSourceLocation());
            txLine.setQuantity(docLine.getOrderedQuantity());
            txLine.setBeforeQuantity(before);
            txLine.setAfterQuantity(after);
            for (InventoryDocumentBatchAllocation allocation : docLine.getBatchAllocations()) {
                InventoryBalance batchBalance = balanceRepository
                        .findForUpdateByBatchId(
                                allocation.getInventoryBatch().getId(), docLine.getSourceLocation().getId())
                        .orElseThrow(() -> new BadRequestException(
                                "Không tìm thấy tồn kho lô " + allocation.getInventoryBatch().getBatchCode()));
                BigDecimal beforeBatch = batchBalance.getQuantityOnHand();
                BigDecimal afterBatch = beforeBatch.add(allocation.getQuantity());
                if (afterBatch.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException(
                            "Tồn kho lô không đủ để điều chỉnh giảm: " + allocation.getInventoryBatch().getBatchCode());
                }
                batchBalance.setQuantityOnHand(afterBatch);
                balanceRepository.save(batchBalance);

                InventoryTransactionBatchAllocation txAllocation = new InventoryTransactionBatchAllocation();
                txAllocation.setInventoryBatch(allocation.getInventoryBatch());
                txAllocation.setQuantity(allocation.getQuantity());
                txAllocation.setBeforeBatchQuantity(beforeBatch);
                txAllocation.setAfterBatchQuantity(afterBatch);
                txLine.addBatchAllocation(txAllocation);
            }
            transaction.addLine(txLine);
        }

        transactionRepository.saveAndFlush(transaction);
    }

    private InventoryAdjustment findDocumentOrThrow(UUID id) {
        return adjustmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment", id));
    }

    private String generateDocumentCode() {
        return "ADJ-" + LocalDate.now().format(CODE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateTransactionCode() {
        return "ADJT-" + LocalDate.now().format(CODE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(SecurityUtils.currentUserId());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("User ID hiện tại không phải UUID hợp lệ");
        }
    }

    private void requireAnyRole(Role... roles) {
        if (SecurityUtils.hasAnyRole(roles)) return;
        throw new AccessDeniedException("Bạn không có quyền thực hiện thao tác điều chỉnh này");
    }
}
