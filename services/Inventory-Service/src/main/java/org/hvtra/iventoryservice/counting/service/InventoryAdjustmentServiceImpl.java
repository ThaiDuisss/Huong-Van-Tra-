package org.hvtra.iventoryservice.counting.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.counting.dto.request.CreateAdjustmentRequest;
import org.hvtra.iventoryservice.counting.dto.response.AdjustmentResponse;
import org.hvtra.iventoryservice.counting.mapper.AdjustmentMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryCountRepository;
import org.hvtra.iventoryservice.counting.repository.InventoryDocumentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final InventoryDocumentRepository documentRepository;
    private final InventoryCountRepository countRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final AdjustmentMapper mapper;

    public InventoryAdjustmentServiceImpl(
            InventoryDocumentRepository documentRepository,
            InventoryCountRepository countRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryTransactionRepository transactionRepository,
            AdjustmentMapper mapper) {
        this.documentRepository = documentRepository;
        this.countRepository = countRepository;
        this.balanceRepository = balanceRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.transactionRepository = transactionRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<AdjustmentResponse> findAll(
            UUID branchId, InventoryDocumentStatus status, Pageable pageable) {
        Specification<InventoryDocument> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.or(
                    cb.equal(root.get("documentType"), InventoryDocumentType.ADJUSTMENT_IN),
                    cb.equal(root.get("documentType"), InventoryDocumentType.ADJUSTMENT_OUT)
            ));
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(
                documentRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public AdjustmentResponse findById(UUID id) {
        InventoryDocument document = findDocumentOrThrow(id);
        return mapper.toResponse(document);
    }

    @Override
    @Transactional
    public AdjustmentResponse create(CreateAdjustmentRequest request) {
        InventoryDocument document = new InventoryDocument();
        document.setDocumentCode(generateDocumentCode());
        document.setBranchId(request.branchId());
        document.setReasonCode(request.reasonCode());
        document.setNote(request.note());

        AtomicInteger lineNo = new AtomicInteger(1);

        if (request.inventoryTakeId() != null) {
            // Create from inventory take
            InventoryCount count = countRepository.findByIdAndIsDeletedFalse(request.inventoryTakeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory count", request.inventoryTakeId()));

            // Delegate validation to entity
            count.validateCanCreateAdjustment();

            // Delegate query to entity
            List<InventoryCountLine> varianceLines = count.getLinesWithVariance();
            if (varianceLines.isEmpty()) {
                throw new BadRequestException("Không có chênh lệch cần điều chỉnh");
            }

            for (InventoryCountLine countLine : varianceLines) {
                InventoryDocumentLine docLine = new InventoryDocumentLine();
                docLine.setLineNo(lineNo.getAndIncrement());
                docLine.setInventoryItem(countLine.getInventoryItem());
                docLine.setSourceLocation(countLine.getLocation());
                docLine.setUnitId(UUID.randomUUID()); // TODO: get from inventory item's unit
                docLine.setRequestedQuantity(countLine.getVarianceQuantity());
                document.addLine(docLine);
            }

            document.setReferenceType(InventoryReferenceType.STOCK_COUNT);
            document.setReferenceId(count.getId().toString());

            // Delegate link to entity
            count.linkAdjustmentDocument(document);
            countRepository.save(count);
        } else {
            // Standalone adjustment
            if (request.lines() == null || request.lines().isEmpty()) {
                throw new BadRequestException("Danh sách dòng điều chỉnh không được rỗng");
            }

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
                docLine.setUnitId(UUID.randomUUID()); // TODO: get from inventory item's unit
                docLine.setRequestedQuantity(lineRequest.adjustmentQuantity());
                docLine.setNote(lineRequest.note());
                document.addLine(docLine);
            }

            document.setReferenceType(InventoryReferenceType.MANUAL);
        }

        // Determine document type based on net adjustment direction
        BigDecimal netQuantity = document.getLines().stream()
                .map(InventoryDocumentLine::getRequestedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        document.setDocumentType(netQuantity.compareTo(BigDecimal.ZERO) >= 0
                ? InventoryDocumentType.ADJUSTMENT_IN
                : InventoryDocumentType.ADJUSTMENT_OUT);

        return mapper.toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public AdjustmentResponse approve(UUID id) {
        InventoryDocument document = findDocumentOrThrow(id);

        // Delegate to entity
        document.approve(document.getCreatedBy()); // TODO: get current user from SecurityContext

        return mapper.toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public AdjustmentResponse post(UUID id) {
        InventoryDocument document = findDocumentOrThrow(id);

        // Delegate validation to entity
        document.validateCanPost();

        // Orchestration: update balances and create transaction
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(document.getDocumentType() == InventoryDocumentType.ADJUSTMENT_IN
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
            BigDecimal after = before.add(docLine.getRequestedQuantity());

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
            txLine.setQuantity(docLine.getRequestedQuantity());
            txLine.setBeforeQuantity(before);
            txLine.setAfterQuantity(after);
            transaction.addLine(txLine);
        }

        transactionRepository.saveAndFlush(transaction);

        // Delegate state transition to entity
        document.post();

        return mapper.toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public AdjustmentResponse reject(UUID id) {
        InventoryDocument document = findDocumentOrThrow(id);

        // Delegate to entity
        document.reject();

        return mapper.toResponse(documentRepository.save(document));
    }

    // ====== Private Helpers ======

    private InventoryDocument findDocumentOrThrow(UUID id) {
        return documentRepository.findByIdAndIsDeletedFalse(id)
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
}
