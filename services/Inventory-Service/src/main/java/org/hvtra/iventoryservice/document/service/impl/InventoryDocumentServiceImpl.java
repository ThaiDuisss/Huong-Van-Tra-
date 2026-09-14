package org.hvtra.iventoryservice.document.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.exception.FeignClientException;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.document.dto.request.*;
import org.hvtra.iventoryservice.document.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.hvtra.iventoryservice.document.mapper.InventoryDocumentMapper;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentBatchAllocationRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentLineRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentRepository;
import org.hvtra.iventoryservice.document.service.InventoryDocumentService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.entity.supplement.InventorySupplementRequest;
import org.hvtra.iventoryservice.domain.entity.supplement.InventorySupplementRequestLine;
import org.hvtra.iventoryservice.domain.entity.supplement.repository.InventorySupplementRequestRepository;
import org.hvtra.iventoryservice.domain.entity.supplierreturn.InventorySupplierReturnRequest;
import org.hvtra.iventoryservice.domain.entity.supplierreturn.InventorySupplierReturnRequestLine;
import org.hvtra.iventoryservice.domain.entity.supplierreturn.repository.InventorySupplierReturnRequestRepository;
import org.hvtra.iventoryservice.domain.enums.*;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
@Slf4j
public class InventoryDocumentServiceImpl implements InventoryDocumentService {

    private final InventoryDocumentRepository documentRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventorySupplementRequestRepository supplementRequestRepository;
    private final InventorySupplierReturnRequestRepository supplierReturnRequestRepository;
    private final CatalogClient catalogClient;
    private final InventoryDocumentMapper mapper;

    public InventoryDocumentServiceImpl(
            InventoryDocumentRepository documentRepository,
            InventoryDocumentLineRepository lineRepository,
            InventoryDocumentBatchAllocationRepository allocationRepository,
            InventoryLocationRepository locationRepository,
            InventoryItemRepository itemRepository,
            InventoryBatchRepository batchRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            InventorySupplementRequestRepository supplementRequestRepository,
            InventorySupplierReturnRequestRepository supplierReturnRequestRepository,
            CatalogClient catalogClient,
            InventoryDocumentMapper mapper
    ) {
        this.documentRepository = documentRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.supplementRequestRepository = supplementRequestRepository;
        this.supplierReturnRequestRepository = supplierReturnRequestRepository;
        this.catalogClient = catalogClient;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<InventoryDocumentResponse> findAll(
            InventoryDocumentType documentType,
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        Specification<InventoryDocument> specification = (root, rootQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (documentType != null) {
                predicates.add(cb.equal(root.get("documentType"), documentType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("locationId"), locationId));
            }
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplierId"), supplierId));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("documentCode")), pattern),
                        cb.like(cb.lower(root.get("referenceId")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        var page = documentRepository.findAll(specification, pageable);
        List<InventoryDocument> documents = page.getContent();
        List<UUID> supplierIds = documents.stream()
                .map(InventoryDocument::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, String> supplierNameMap = new HashMap<>();

        if (!supplierIds.isEmpty()) {
            try {
                List<CatalogClient.SupplierResponse> suppliers = catalogClient.getSuppliersBatch(supplierIds);
                if (suppliers != null) {
                    for (CatalogClient.SupplierResponse s : suppliers) {
                        if (s != null && s.id() != null && s.name() != null) {
                            supplierNameMap.put(s.id(), s.name());
                        }
                    }
                }
            } catch (FeignClientException exception) {
                log.error("Feign client error", exception);
            }
        }
        return PageResponse.from(page.map(doc -> mapper.toResponse(doc, supplierNameMap.get(doc.getSupplierId()))));
    }

    @Override
    public GoodsReceiptSummaryCountResponse getSummaryCounts(InventoryDocumentType documentType) {
        InventoryDocumentType type = documentType != null ? documentType : InventoryDocumentType.PURCHASE_RECEIPT;
        List<Object[]> rows = documentRepository.countByStatusGroupByDocumentType(type);

        Map<InventoryDocumentStatus, Long> statusCounts = new EnumMap<>(InventoryDocumentStatus.class);
        long total = 0;
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] instanceof InventoryDocumentStatus s && row[1] instanceof Number count) {
                statusCounts.put(s, count.longValue());
                total += count.longValue();
            }
        }

        long draft = statusCounts.getOrDefault(InventoryDocumentStatus.DRAFT, 0L);
        long pendingApproval = statusCounts.getOrDefault(InventoryDocumentStatus.PENDING_APPROVAL, 0L);
        long receiving = statusCounts.getOrDefault(InventoryDocumentStatus.RECEIVING, 0L)
                + statusCounts.getOrDefault(InventoryDocumentStatus.APPROVED, 0L);
        long awaitingQc = statusCounts.getOrDefault(InventoryDocumentStatus.QC_PENDING, 0L);
        long readyToPost = statusCounts.getOrDefault(InventoryDocumentStatus.QC_COMPLETED, 0L);
        long postedToday = statusCounts.getOrDefault(InventoryDocumentStatus.POSTED, 0L);

        return new GoodsReceiptSummaryCountResponse(
                draft,
                pendingApproval,
                receiving,
                awaitingQc,
                readyToPost,
                postedToday,
                total
        );
    }

    private InventoryDocumentResponse toEnrichedResponse(InventoryDocument document) {
        if (document == null) {
            return null;
        }
        String supplierName = null;
        if (document.getSupplierId() != null) {
            try {
                CatalogClient.SupplierResponse s = catalogClient.getSupplier(document.getSupplierId());
                if (s != null) {
                    supplierName = s.name();
                }
            } catch (Exception ignored) {
            }
        }
        return mapper.toResponse(document, supplierName);
    }

    @Override
    public InventoryDocumentResponse findById(UUID id) {
        InventoryDocument document = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryDocument", id));
        return toEnrichedResponse(document);
    }

    private UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InternalUserPrincipal principal) {
            String userId = principal.userId();
            if (userId != null && !userId.isBlank()) {
                try {
                    return UUID.fromString(userId);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    @Transactional
    public InventoryDocumentResponse createPurchaseReceipt(CreatePurchaseReceiptRequest request) {
        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", request.locationId()));

        InventoryDocument document = new InventoryDocument();
        document.setDocumentCode(generateDocumentCode(InventoryDocumentType.PURCHASE_RECEIPT));
        document.setDocumentType(InventoryDocumentType.PURCHASE_RECEIPT);
        document.setStatus(InventoryDocumentStatus.DRAFT);
        document.setCreatedBy(getCurrentUserId());
        document.setLocationId(location.getId());
        document.setDestinationLocation(location);
        document.setBranchId(request.branchId() != null ? request.branchId() : location.getBranchId());
        document.setSupplierId(request.supplierId());
        document.setReferenceType(request.referenceType() != null ? request.referenceType() : InventoryReferenceType.PURCHASE_ORDER);
        document.setReferenceId(request.referenceId());
        document.setExpectedDate(request.expectedDate());
        document.setNote(request.note());

        int lineNo = 1;
        for (var itemReq : request.items()) {
            InventoryItem item = itemRepository.findById(itemReq.inventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemReq.inventoryItemId()));

            InventoryDocumentLine line = new InventoryDocumentLine();
            line.setLineNo(lineNo++);
            line.setInventoryItem(item);
            line.setUnitId(itemReq.unitId() != null ? itemReq.unitId() : item.getUnitId());
            line.setOrderedQuantity(itemReq.orderedQuantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setShortageQuantity(BigDecimal.ZERO);
            line.setExcessQuantity(BigDecimal.ZERO);
            line.setAcceptedQuantity(BigDecimal.ZERO);
            line.setRejectedQuantity(BigDecimal.ZERO);
            line.setDestinationLocation(location);
            line.setNote(itemReq.note());

            document.addLine(line);
        }

        InventoryDocument saved = documentRepository.save(document);
        return toEnrichedResponse(saved);
    }

    @Override
    @Transactional
    public InventoryDocumentResponse updatePurchaseReceipt(UUID id, UpdatePurchaseReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT documents can be updated");
        }

        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", request.locationId()));

        document.setLocationId(location.getId());
        document.setDestinationLocation(location);
        document.setBranchId(request.branchId() != null ? request.branchId() : location.getBranchId());
        document.setSupplierId(request.supplierId());
        document.setReferenceType(request.referenceType());
        document.setReferenceId(request.referenceId());
        document.setExpectedDate(request.expectedDate());
        document.setNote(request.note());

        document.getLines().clear();
        int lineNo = 1;
        for (var itemReq : request.items()) {
            InventoryItem item = itemRepository.findById(itemReq.inventoryItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemReq.inventoryItemId()));

            InventoryDocumentLine line = new InventoryDocumentLine();
            line.setLineNo(lineNo++);
            line.setInventoryItem(item);
            line.setUnitId(itemReq.unitId() != null ? itemReq.unitId() : item.getUnitId());
            line.setOrderedQuantity(itemReq.orderedQuantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setShortageQuantity(BigDecimal.ZERO);
            line.setExcessQuantity(BigDecimal.ZERO);
            line.setAcceptedQuantity(BigDecimal.ZERO);
            line.setRejectedQuantity(BigDecimal.ZERO);
            line.setDestinationLocation(location);
            line.setNote(itemReq.note());

            document.addLine(line);
        }

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse submitForApproval(UUID id) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT documents can be submitted for approval");
        }
        if (document.getLines().isEmpty()) {
            throw new BadRequestException("Cannot submit a document with no line items");
        }

        document.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse approve(UUID id, ApproveReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL documents can be approved");
        }

        document.setStatus(InventoryDocumentStatus.APPROVED);
        document.setApprovedBy(getCurrentUserId());
        document.setApprovedAt(Instant.now());
        if (request != null && request.approvalNote() != null) {
            document.setApprovalNote(request.approvalNote());
        }

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse reject(UUID id, RejectReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL documents can be rejected");
        }

        document.setStatus(InventoryDocumentStatus.REJECTED);
        document.setRejectedBy(getCurrentUserId());
        document.setRejectedAt(Instant.now());
        document.setRejectionReason(request.rejectionReason());

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse startReceiving(UUID id) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED documents can start receiving");
        }

        document.setStatus(InventoryDocumentStatus.RECEIVING);
        document.setReceivedBy(getCurrentUserId());
        document.setReceivedAt(Instant.now());

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse confirmReceiving(UUID id, ReceiveReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.RECEIVING) {
            throw new BadRequestException("Only RECEIVING documents can be confirmed");
        }

        if (document.getReceivedBy() == null) {
            document.setReceivedBy(getCurrentUserId());
        }
        if (document.getReceivedAt() == null) {
            document.setReceivedAt(Instant.now());
        }

        boolean hasShortage = false;
        List<InventorySupplementRequestLine> supplementLines = new ArrayList<>();

        for (var reqItem : request.items()) {
            InventoryDocumentLine line = document.getLines().stream()
                    .filter(l -> l.getId().equals(reqItem.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Line item not found: " + reqItem.lineId()));

            BigDecimal received = reqItem.receivedQuantity();
            line.setReceivedQuantity(received);
            if (reqItem.note() != null) {
                line.setNote(reqItem.note());
            }

            BigDecimal ordered = line.getOrderedQuantity();
            if (ordered != null && received != null) {
                BigDecimal shortage = ordered.subtract(received).max(BigDecimal.ZERO);
                BigDecimal excess = received.subtract(ordered).max(BigDecimal.ZERO);
                line.setShortageQuantity(shortage);
                line.setExcessQuantity(excess);

                if (shortage.compareTo(BigDecimal.ZERO) > 0) {
                    hasShortage = true;
                    InventorySupplementRequestLine suppLine = new InventorySupplementRequestLine();
                    suppLine.setReceiptLineId(line.getId());
                    suppLine.setInventoryItem(line.getInventoryItem());
                    suppLine.setQuantity(shortage);
                    suppLine.setReason("SHORTAGE");
                    supplementLines.add(suppLine);
                }
            }
        }

        if (hasShortage && !supplementLines.isEmpty()) {
            InventorySupplementRequest suppRequest = new InventorySupplementRequest();
            suppRequest.setRequestCode("SR-" + document.getDocumentCode());
            suppRequest.setReferenceType("PURCHASE_RECEIPT");
            suppRequest.setReferenceId(document.getDocumentCode());
            suppRequest.setStatus("PENDING_APPROVAL");
            suppRequest.setNote("Yêu cầu giao bù hàng thiếu từ phiếu nhập " + document.getDocumentCode());
            for (var sl : supplementLines) {
                suppRequest.addLine(sl);
            }
            supplementRequestRepository.save(suppRequest);
        }

        document.setStatus(InventoryDocumentStatus.QC_PENDING);
        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse completeQc(UUID id, QcReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.QC_PENDING) {
            throw new BadRequestException("Only QC_PENDING documents can complete QC");
        }

        boolean hasRejection = false;
        List<InventorySupplierReturnRequestLine> returnLines = new ArrayList<>();

        for (var qcItem : request.items()) {
            InventoryDocumentLine line = document.getLines().stream()
                    .filter(l -> l.getId().equals(qcItem.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Line item not found: " + qcItem.lineId()));

            BigDecimal accepted = qcItem.acceptedQuantity();
            BigDecimal rejected = qcItem.rejectedQuantity();
            BigDecimal received = line.getReceivedQuantity();

            if (accepted.add(rejected).compareTo(received) != 0) {
                throw new BadRequestException("Accepted quantity (" + accepted + ") + Rejected quantity (" + rejected +
                        ") must equal received quantity (" + received + ") for line: " + line.getLineNo());
            }

            line.setAcceptedQuantity(accepted);
            line.setRejectedQuantity(rejected);

            line.getBatchAllocations().clear();
            if (qcItem.batches() != null && !qcItem.batches().isEmpty()) {
                BigDecimal batchTotal = BigDecimal.ZERO;
                for (var batchInput : qcItem.batches()) {
                    if (batchInput.quantity() == null || batchInput.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    batchTotal = batchTotal.add(batchInput.quantity());

                    String batchCode = batchInput.batchCode() != null && !batchInput.batchCode().isBlank()
                            ? batchInput.batchCode().trim()
                            : generateBatchCode(line.getInventoryItem().getReferenceCode());

                    InventoryBatch batch = batchRepository.findByInventoryItemIdAndBatchCode(line.getInventoryItem().getId(), batchCode)
                            .orElseGet(() -> {
                                InventoryBatch newBatch = new InventoryBatch();
                                newBatch.setInventoryItem(line.getInventoryItem());
                                newBatch.setBatchCode(batchCode);
                                newBatch.setSupplierBatchCode(batchInput.supplierLot());
                                newBatch.setManufactureDate(batchInput.manufactureDate());
                                newBatch.setExpiryDate(batchInput.expiryDate());
                                newBatch.setStatus(InventoryBatchStatus.AVAILABLE);
                                return batchRepository.save(newBatch);
                            });

                    InventoryDocumentBatchAllocation allocation = new InventoryDocumentBatchAllocation();
                    allocation.setInventoryBatch(batch);
                    allocation.setQuantity(batchInput.quantity());
                    line.addBatchAllocation(allocation);
                }

                if (batchTotal.compareTo(accepted) != 0) {
                    throw new BadRequestException("Total batch allocated quantity (" + batchTotal +
                            ") must equal accepted quantity (" + accepted + ") for line: " + line.getLineNo());
                }
            }

            if (rejected.compareTo(BigDecimal.ZERO) > 0) {
                hasRejection = true;
                InventorySupplierReturnRequestLine returnLine = new InventorySupplierReturnRequestLine();
                returnLine.setReceiptLineId(line.getId());
                returnLine.setInventoryItem(line.getInventoryItem());
                returnLine.setQuantity(rejected);
                returnLine.setReason("QC_FAILED");
                returnLines.add(returnLine);
            }
        }

        if (hasRejection && !returnLines.isEmpty()) {
            InventorySupplierReturnRequest returnRequest = new InventorySupplierReturnRequest();
            returnRequest.setRequestCode("RR-" + document.getDocumentCode());
            returnRequest.setReferenceType("PURCHASE_RECEIPT");
            returnRequest.setReferenceId(document.getDocumentCode());
            returnRequest.setSupplierId(document.getSupplierId());
            returnRequest.setStatus("PENDING_APPROVAL");
            returnRequest.setNote("Yêu cầu trả hàng lỗi QC từ phiếu nhập " + document.getDocumentCode());
            for (var rl : returnLines) {
                returnRequest.addLine(rl);
            }
            supplierReturnRequestRepository.save(returnRequest);
        }

        document.setStatus(InventoryDocumentStatus.QC_COMPLETED);
        document.setQcBy(getCurrentUserId());
        document.setQcAt(Instant.now());
        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse post(UUID id) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.QC_COMPLETED) {
            throw new BadRequestException("Only QC_COMPLETED documents can be posted");
        }

        InventoryLocation location = locationRepository.findById(document.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", document.getLocationId()));

        UUID branchId = document.getBranchId() != null ? document.getBranchId() : location.getBranchId();

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(InventoryTransactionType.STOCK_IN);
        transaction.setReferenceType(InventoryReferenceType.PURCHASE_RECEIPT);
        transaction.setReferenceId(document.getId().toString());
        transaction.setBranchId(branchId != null ? branchId : location.getId());
        transaction.setInventoryDocument(document);

        for (InventoryDocumentLine docLine : document.getLines()) {
            BigDecimal accepted = docLine.getAcceptedQuantity();
            if (accepted == null || accepted.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            InventoryItem item = docLine.getInventoryItem();

            if (docLine.getBatchAllocations() != null && !docLine.getBatchAllocations().isEmpty()) {
                for (InventoryDocumentBatchAllocation alloc : docLine.getBatchAllocations()) {
                    InventoryBatch batch = alloc.getInventoryBatch();
                    InventoryBalance balance = balanceRepository.findForUpdateByBatchId(batch.getId(), location.getId())
                            .orElseGet(() -> {
                                InventoryBalance newBal = new InventoryBalance();
                                newBal.setInventoryItem(item);
                                newBal.setInventoryBatch(batch);
                                newBal.setLocation(location);
                                newBal.setQuantityOnHand(BigDecimal.ZERO);
                                newBal.setQuantityReserved(BigDecimal.ZERO);
                                newBal.setQuantityBlocked(BigDecimal.ZERO);
                                return newBal;
                            });

                    BigDecimal before = balance.getQuantityOnHand();
                    BigDecimal after = before.add(alloc.getQuantity());
                    balance.setQuantityOnHand(after);
                    balanceRepository.save(balance);

                    InventoryTransactionLine transLine = new InventoryTransactionLine();
                    transLine.setInventoryItem(item);
                    transLine.setLocation(location);
                    transLine.setQuantity(alloc.getQuantity());
                    transLine.setBeforeQuantity(before);
                    transLine.setAfterQuantity(after);

                    InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
                    transAlloc.setInventoryBatch(batch);
                    transAlloc.setQuantity(alloc.getQuantity());
                    transAlloc.setBeforeBatchQuantity(before);
                    transAlloc.setAfterBatchQuantity(after);
                    transLine.addBatchAllocation(transAlloc);

                    transaction.addLine(transLine);
                }
            } else {
                InventoryBalance balance = balanceRepository.findForUpdate(item.getId(), location.getId())
                        .orElseGet(() -> {
                            InventoryBalance newBal = new InventoryBalance();
                            newBal.setInventoryItem(item);
                            newBal.setLocation(location);
                            newBal.setQuantityOnHand(BigDecimal.ZERO);
                            newBal.setQuantityReserved(BigDecimal.ZERO);
                            newBal.setQuantityBlocked(BigDecimal.ZERO);
                            return newBal;
                        });

                BigDecimal before = balance.getQuantityOnHand();
                BigDecimal after = before.add(accepted);
                balance.setQuantityOnHand(after);
                balanceRepository.save(balance);

                InventoryTransactionLine transLine = new InventoryTransactionLine();
                transLine.setInventoryItem(item);
                transLine.setLocation(location);
                transLine.setQuantity(accepted);
                transLine.setBeforeQuantity(before);
                transLine.setAfterQuantity(after);

                transaction.addLine(transLine);
            }
        }

        transactionRepository.save(transaction);

        document.setStatus(InventoryDocumentStatus.POSTED);
        document.setPostedBy(getCurrentUserId());
        document.setPostedAt(Instant.now());

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse cancel(UUID id, String note) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() == InventoryDocumentStatus.POSTED) {
            throw new BadRequestException("Cannot cancel an already POSTED document");
        }

        document.setStatus(InventoryDocumentStatus.CANCELLED);
        if (note != null && !note.isBlank()) {
            document.setNote((document.getNote() != null ? document.getNote() + " | " : "") + "Hủy phiếu: " + note);
        }

        return toEnrichedResponse(documentRepository.save(document));
    }

    private InventoryDocument getDocument(UUID id) {
        return documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryDocument", id));
    }

    private String generateDocumentCode(InventoryDocumentType type) {
        String prefix = type == InventoryDocumentType.PURCHASE_RECEIPT ? "NK" : "DOC";
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return prefix + "-" + date + "-" + suffix;
    }

    private String generateBatchCode(String sku) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "LOT-" + (sku != null ? sku + "-" : "") + date + "-" + suffix;
    }

    private String generateTransactionCode() {
        return "TX-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
