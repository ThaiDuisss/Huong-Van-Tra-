package org.hvtra.iventoryservice.document.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.document.dto.request.*;
import org.hvtra.iventoryservice.document.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentBatchAllocationResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentExportResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentListItemResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentLineResponse;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentBatchAllocationRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentExportRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentListItemRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentLineRow;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
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
import org.hvtra.iventoryservice.domain.enums.*;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ErrorCode;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private final InventoryDocumentLineRepository lineRepository;
    private final InventoryDocumentBatchAllocationRepository allocationRepository;

    private final CatalogClient catalogClient;
    private final IdentityClient identityClient;
    private final InventoryDocumentMapper mapper;
    private final SupplyLeadTimeService supplyLeadTimeService;

    public InventoryDocumentServiceImpl(
            InventoryDocumentRepository documentRepository,
            InventoryDocumentLineRepository lineRepository,
            InventoryDocumentBatchAllocationRepository allocationRepository,
            InventoryLocationRepository locationRepository,
            InventoryItemRepository itemRepository,
            InventoryBatchRepository batchRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            InventoryDocumentLineRepository lineRepository1, InventoryDocumentBatchAllocationRepository allocationRepository1,
            CatalogClient catalogClient,
            IdentityClient identityClient,
            InventoryDocumentMapper mapper,
            SupplyLeadTimeService supplyLeadTimeService
    ) {
        this.documentRepository = documentRepository;
        this.locationRepository = locationRepository;
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.lineRepository = lineRepository1;
        this.allocationRepository = allocationRepository1;
        this.catalogClient = catalogClient;
        this.identityClient = identityClient;
        this.mapper = mapper;
        this.supplyLeadTimeService = supplyLeadTimeService;
    }

    private static final int MAX_EXPORT_ROWS = 10_000;

    @Override
    public PageResponse<InventoryDocumentListItemResponse> findAll(
            InventoryDocumentType documentType,
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        String documentTypeParam = documentType != null ? documentType.name() : null;
        String statusParam = status != null ? status.name() : null;
        String queryPattern = toQueryPattern(query);

        long totalElements = documentRepository.countDocument(
                documentTypeParam, statusParam, locationId, supplierId, fromDate, toDate, queryPattern);

        List<InventoryDocumentListItemRow> rows = totalElements == 0
                ? List.of()
                : documentRepository.searchDocumentListItems(
                        documentTypeParam, statusParam, locationId, supplierId, fromDate, toDate, queryPattern,
                        pageable.getPageSize(), pageable.getOffset());

        Map<UUID, String> supplierNameMap = fetchSupplierNames(distinctUuids(
                rows.stream().map(InventoryDocumentListItemRow::getSupplierId)));

        List<InventoryDocumentListItemResponse> content = rows.stream()
                .map(row -> {
                    UUID rowSupplierId = parseUuid(row.getSupplierId());
                    return new InventoryDocumentListItemResponse(
                            parseUuid(row.getId()),
                            row.getDocumentCode(),
                            InventoryDocumentStatus.valueOf(row.getStatus()),
                            rowSupplierId,
                            rowSupplierId != null ? supplierNameMap.get(rowSupplierId) : null,
                            parseUuid(row.getLocationId()),
                            row.getLocationName(),
                            nvl(row.getTotalReceivedQuantity()),
                            row.getReceivedAt(),
                            parseUuid(row.getCreatedBy()),
                            row.getCreatedAt()
                    );
                })
                .toList();

        Page<InventoryDocumentListItemResponse> page = new PageImpl<>(content, pageable, totalElements);
        return PageResponse.from(page);
    }

    @Override
    public List<InventoryDocumentExportResponse> export(
            InventoryDocumentType documentType,
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate
    ) {
        List<InventoryDocumentExportRow> rows = documentRepository.searchDocumentExportRows(
                documentType != null ? documentType.name() : null,
                status != null ? status.name() : null,
                locationId, supplierId, fromDate, toDate, toQueryPattern(query),
                MAX_EXPORT_ROWS);

        Map<UUID, String> supplierNameMap = fetchSupplierNames(distinctUuids(
                rows.stream().map(InventoryDocumentExportRow::getSupplierId)));
        Map<UUID, String> userNameMap = fetchUserNames(distinctUuids(
                rows.stream().map(InventoryDocumentExportRow::getCreatedBy)));

        return rows.stream()
                .map(row -> {
                    UUID rowSupplierId = parseUuid(row.getSupplierId());
                    UUID createdBy = parseUuid(row.getCreatedBy());
                    return new InventoryDocumentExportResponse(
                            row.getDocumentCode(),
                            row.getReferenceId(),
                            rowSupplierId != null ? supplierNameMap.get(rowSupplierId) : null,
                            row.getLocationName(),
                            InventoryDocumentStatus.valueOf(row.getStatus()),
                            row.getTotalLines() != null ? row.getTotalLines() : 0L,
                            nvl(row.getTotalReceivedQuantity()),
                            nvl(row.getTotalAcceptedQuantity()),
                            nvl(row.getTotalRejectedQuantity()),
                            row.getReceivedAt(),
                            row.getCreatedAt(),
                            createdBy != null ? userNameMap.get(createdBy) : null
                    );
                })
                .toList();
    }

    private String toQueryPattern(String query) {
        return (query != null && !query.isBlank())
                ? "%" + query.trim().toLowerCase() + "%"
                : null;
    }

    private List<UUID> distinctUuids(java.util.stream.Stream<String> values) {
        return values.filter(Objects::nonNull).map(this::parseUuid).distinct().toList();
    }

    private InventoryDocumentResponse buildDetailResponse(InventoryDocumentRow header) {
        UUID documentId = parseUuid(header.getId());
        List<InventoryDocumentLineRow> lineRows = lineRepository.findLineRowsByDocumentIds(List.of(documentId));

        List<UUID> lineIds = lineRows.stream().map(l -> parseUuid(l.getId())).toList();
        Map<String, List<InventoryDocumentBatchAllocationRow>> allocationsByLine = lineIds.isEmpty()
                ? Map.of()
                : allocationRepository.findBatchAllocationRowsByLineIds(lineIds).stream()
                        .collect(Collectors.groupingBy(InventoryDocumentBatchAllocationRow::getDocumentLineId));

        UUID headerSupplierId = parseUuid(header.getSupplierId());
        String supplierName = headerSupplierId != null
                ? fetchSupplierNames(List.of(headerSupplierId)).get(headerSupplierId)
                : null;

        Map<UUID, String> userNameMap = fetchUserNames(distinctUuids(java.util.stream.Stream.of(
                header.getCreatedBy(), header.getApprovedBy(), header.getRejectedBy(),
                header.getReceivedBy(), header.getQcBy(), header.getPostedBy())));

        return toDocumentResponse(header, lineRows, allocationsByLine, supplierName, userNameMap);
    }

    private UUID parseUuid(String value) {
        return value != null ? UUID.fromString(value) : null;
    }

    private Map<UUID, String> fetchSupplierNames(List<UUID> supplierIds) {
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
        return supplierNameMap;
    }

    // createdBy/approvedBy... lưu Keycloak subject; tên lấy từ Identity-Service, lỗi thì bỏ qua (chỉ phục vụ hiển thị)
    private Map<UUID, String> fetchUserNames(Collection<UUID> userIds) {
        Map<UUID, String> userNameMap = new HashMap<>();
        if (userIds.isEmpty()) {
            return userNameMap;
        }
        try {
            List<IdentityClient.UserBriefResponse> users = identityClient.getUsersBatch(
                    userIds.stream().map(UUID::toString).toList());
            if (users != null) {
                for (IdentityClient.UserBriefResponse u : users) {
                    UUID userId = u != null ? tryParseUuid(u.keycloakSubject()) : null;
                    String name = u == null ? null
                            : (u.fullName() != null && !u.fullName().isBlank()) ? u.fullName() : u.username();
                    if (userId != null && name != null && !name.isBlank()) {
                        userNameMap.put(userId, name);
                    }
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Không lấy được tên người dùng từ Identity-Service: {}", exception.getMessage());
        }
        return userNameMap;
    }

    private String userNameOf(Map<UUID, String> userNameMap, String userId) {
        return userId != null ? userNameMap.get(parseUuid(userId)) : null;
    }

    private UUID tryParseUuid(String value) {
        try {
            return value != null ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private InventoryDocumentResponse toDocumentResponse(
            InventoryDocumentRow header,
            List<InventoryDocumentLineRow> lineRows,
            Map<String, List<InventoryDocumentBatchAllocationRow>> allocationsByLine,
            String supplierName,
            Map<UUID, String> userNameMap
    ) {
        List<InventoryDocumentLineResponse> lines = new ArrayList<>(lineRows.size());
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalAccepted = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;

        for (InventoryDocumentLineRow line : lineRows) {
            List<InventoryDocumentBatchAllocationResponse> allocations = allocationsByLine
                    .getOrDefault(line.getId(), List.of())
                    .stream()
                    .map(this::toAllocationResponse)
                    .toList();

            BigDecimal ordered = nvl(line.getOrderedQuantity());
            BigDecimal received = nvl(line.getReceivedQuantity());
            BigDecimal accepted = nvl(line.getAcceptedQuantity());
            BigDecimal rejected = nvl(line.getRejectedQuantity());

            totalOrdered = totalOrdered.add(ordered);
            totalReceived = totalReceived.add(received);
            totalAccepted = totalAccepted.add(accepted);
            totalRejected = totalRejected.add(rejected);

            String itemName = line.getItemName() != null ? line.getItemName() : line.getItemSku();
            lines.add(new InventoryDocumentLineResponse(
                    parseUuid(line.getId()),
                    line.getLineNo(),
                    parseUuid(line.getInventoryItemId()),
                    line.getItemSku(),
                    itemName,
                    parseUuid(line.getUnitId()),
                    null,
                    ordered,
                    received,
                    nvl(line.getShortageQuantity()),
                    nvl(line.getExcessQuantity()),
                    accepted,
                    rejected,
                    line.getNote(),
                    allocations
            ));
        }

        return new InventoryDocumentResponse(
                parseUuid(header.getId()),
                header.getDocumentCode(),
                InventoryDocumentType.valueOf(header.getDocumentType()),
                InventoryDocumentStatus.valueOf(header.getStatus()),
                parseUuid(header.getBranchId()),
                parseUuid(header.getLocationId()),
                header.getLocationName(),
                header.getLocationCode(),
                parseUuid(header.getSupplierId()),
                supplierName,
                header.getReferenceType() != null ? InventoryReferenceType.valueOf(header.getReferenceType()) : null,
                header.getReferenceId(),
                header.getExpectedDate(),
                header.getNote(),
                header.getCreatedAt(),
                header.getUpdatedAt(),
                parseUuid(header.getCreatedBy()),
                userNameOf(userNameMap, header.getCreatedBy()),
                parseUuid(header.getApprovedBy()),
                userNameOf(userNameMap, header.getApprovedBy()),
                header.getApprovedAt(),
                header.getApprovalNote(),
                parseUuid(header.getRejectedBy()),
                userNameOf(userNameMap, header.getRejectedBy()),
                header.getRejectedAt(),
                header.getRejectionReason(),
                parseUuid(header.getReceivedBy()),
                userNameOf(userNameMap, header.getReceivedBy()),
                header.getReceivedAt(),
                parseUuid(header.getQcBy()),
                userNameOf(userNameMap, header.getQcBy()),
                header.getQcAt(),
                parseUuid(header.getPostedBy()),
                userNameOf(userNameMap, header.getPostedBy()),
                header.getPostedAt(),
                lines,
                lines.size(),
                totalOrdered,
                totalReceived,
                totalAccepted,
                totalRejected
        );
    }

    private InventoryDocumentBatchAllocationResponse toAllocationResponse(InventoryDocumentBatchAllocationRow row) {
        return new InventoryDocumentBatchAllocationResponse(
                parseUuid(row.getId()),
                parseUuid(row.getInventoryBatchId()),
                row.getBatchCode(),
                row.getSupplierBatchCode(),
                row.getManufactureDate(),
                row.getExpiryDate(),
                row.getQuantity()
        );
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
        long approved = statusCounts.getOrDefault(InventoryDocumentStatus.APPROVED, 0L);
        long receiving = statusCounts.getOrDefault(InventoryDocumentStatus.RECEIVING, 0L);
        long pendingExceptionApproval = statusCounts.getOrDefault(InventoryDocumentStatus.PENDING_EXCEPTION_APPROVAL, 0L);
        long awaitingQc = 0L;
        long readyToPost = 0L;
        long posted = statusCounts.getOrDefault(InventoryDocumentStatus.POSTED, 0L);
        long postedToday = posted;
        long rejected = statusCounts.getOrDefault(InventoryDocumentStatus.REJECTED, 0L);
        long cancelled = statusCounts.getOrDefault(InventoryDocumentStatus.CANCELLED, 0L);

        return new GoodsReceiptSummaryCountResponse(
                draft,
                pendingApproval,
                approved,
                receiving,
                pendingExceptionApproval,
                awaitingQc,
                readyToPost,
                posted,
                postedToday,
                rejected,
                cancelled,
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
        Map<UUID, String> userNameMap = fetchUserNames(java.util.stream.Stream.of(
                        document.getCreatedBy(), document.getApprovedBy(), document.getRejectedBy(),
                        document.getReceivedBy(), document.getQcBy(), document.getPostedBy())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return mapper.toResponse(document, supplierName, userNameMap);
    }

    @Override
    public InventoryDocumentResponse findById(UUID id) {
        InventoryDocumentRow header = documentRepository.findDocumentRowById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryDocument", id));
        return buildDetailResponse(header);
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
        InventoryDocument document = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> ErrorCode.INVENTORY_DOCUMENT_NOT_FOUND.exception());

        if (!isAdminOrCreator(document)) {
            throw ErrorCode.INVENTORY_DOCUMENT_EDIT_FORBIDDEN.exception();
        }

        if (document.getStatus() != InventoryDocumentStatus.DRAFT
                && document.getStatus() != InventoryDocumentStatus.REJECTED) {
            throw ErrorCode.INVENTORY_DOCUMENT_NOT_EDITABLE.exception();
        }

        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> ErrorCode.INVENTORY_LOCATION_NOT_FOUND.exception());

        Set<UUID> itemIds = request.items().stream()
                .map(itemReq -> itemReq.inventoryItemId())
                .collect(Collectors.toSet());
        Map<UUID, InventoryItem> itemMap = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(InventoryItem::getId, item -> item));

        // Phiếu bị từ chối sau khi sửa quay về bản nháp để gửi duyệt lại — xóa thông tin từ chối cũ
        document.setStatus(InventoryDocumentStatus.DRAFT);
        document.setRejectedBy(null);
        document.setRejectedAt(null);
        document.setRejectionReason(null);

        document.setLocationId(location.getId());
        document.setDestinationLocation(location);
        document.setBranchId(request.branchId() != null ? request.branchId() : location.getBranchId());
        document.setSupplierId(request.supplierId());
        document.setReferenceType(request.referenceType());
        document.setReferenceId(request.referenceId());
        document.setExpectedDate(request.expectedDate());
        document.setNote(request.note());

        document.getLines().clear();
        // Flush để DELETE các dòng cũ chạy trước INSERT dòng mới — Hibernate mặc định INSERT trước,
        // sẽ vi phạm unique (document_id, line_no)
        documentRepository.flush();
        int lineNo = 1;
        for (var itemReq : request.items()) {
            InventoryItem item = itemMap.get(itemReq.inventoryItemId());
            if (item == null) {
                throw ErrorCode.INVENTORY_ITEM_NOT_FOUND.exception();
            }

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
            }
        }

        return toEnrichedResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse completeQc(UUID id, QcReceiptRequest request) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.RECEIVING) {
            throw new BadRequestException("Only RECEIVING documents can complete QC");
        }

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
        }

        InventoryDocumentStatus nextStatus = hasException(document)
                ? InventoryDocumentStatus.PENDING_EXCEPTION_APPROVAL
                : InventoryDocumentStatus.POSTED;
        document.setStatus(nextStatus);
        document.setQcBy(getCurrentUserId());
        document.setQcAt(Instant.now());

        InventoryDocument savedDocument = documentRepository.save(document);

        // Auto post nếu QC pass (không có exception)
        if (nextStatus == InventoryDocumentStatus.POSTED) {
            performPost(savedDocument);
            savedDocument.setStatus(InventoryDocumentStatus.POSTED);
            savedDocument = documentRepository.save(savedDocument);
            supplyLeadTimeService.onReceiptPosted(savedDocument);
        }

        return toEnrichedResponse(savedDocument);
    }

    @Override
    @Transactional
    public InventoryDocumentResponse completeInspection(UUID id, CompleteInspectionRequest request) {
        // Step 1: Confirm receiving
        confirmReceiving(id, new ReceiveReceiptRequest(
            request.receiveItems().stream()
                .map(item -> new ReceiveReceiptRequest.ReceiveItemRequest(
                    item.lineId(),
                    item.receivedQuantity(),
                    item.note()
                ))
                .toList()
        ));

        // Step 2: Complete QC
        return completeQc(id, new QcReceiptRequest(
            request.qcItems().stream()
                .map(item -> new QcReceiptRequest.QcItemRequest(
                    item.lineId(),
                    item.acceptedQuantity(),
                    item.rejectedQuantity(),
                    item.batches().stream()
                        .map(batch -> new QcReceiptRequest.BatchAllocationInput(
                            batch.batchCode(),
                            batch.manufactureDate(),
                            batch.expiryDate(),
                            batch.quantity(),
                            batch.supplierLot()
                        ))
                        .toList()
                ))
                .toList()
        ));
    }

    @Override
    @Transactional
    public InventoryDocumentResponse post(UUID id) {
        InventoryDocument document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_EXCEPTION_APPROVAL) {
            throw new BadRequestException("Only PENDING_EXCEPTION_APPROVAL documents can be posted");
        }

        performPost(document);
        document.setStatus(InventoryDocumentStatus.POSTED);

        InventoryDocument posted = documentRepository.save(document);
        supplyLeadTimeService.onReceiptPosted(posted);
        return toEnrichedResponse(posted);
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

    @Override
    @Transactional
    public void delete(UUID id) {
        InventoryDocument document = documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ErrorCode.INVENTORY_DOCUMENT_NOT_FOUND.exception());

        if (!isAdminOrCreator(document)) {
            throw ErrorCode.INVENTORY_DOCUMENT_DELETE_FORBIDDEN.exception();
        }

        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw ErrorCode.INVENTORY_DOCUMENT_NOT_DELETABLE.exception();
        }

        document.setIsDeleted(true);
        documentRepository.save(document);
    }

    private void performPost(InventoryDocument document) {
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
        document.setPostedBy(getCurrentUserId());
        document.setPostedAt(Instant.now());
    }

    // createdBy lưu Keycloak subject của người tạo — trùng với userId trong principal
    private boolean isAdminOrCreator(InventoryDocument document) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        UUID currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(document.getCreatedBy());
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

    private boolean hasException(InventoryDocument document) {
        for (InventoryDocumentLine line : document.getLines()) {
            // Check rejected quantity
            if (line.getRejectedQuantity() != null && line.getRejectedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
            // Check shortage/excess (thừa/thiếu)
            BigDecimal ordered = line.getOrderedQuantity() != null ? line.getOrderedQuantity() : BigDecimal.ZERO;
            BigDecimal received = line.getReceivedQuantity() != null ? line.getReceivedQuantity() : BigDecimal.ZERO;
            if (ordered.compareTo(received) != 0) {
                return true;
            }
        }
        return false;
    }
}
