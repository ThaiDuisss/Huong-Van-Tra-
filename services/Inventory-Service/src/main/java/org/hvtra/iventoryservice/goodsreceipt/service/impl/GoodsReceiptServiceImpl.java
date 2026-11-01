package org.hvtra.iventoryservice.goodsreceipt.service.impl;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.iventoryservice.availability.service.SupplyLeadTimeService;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.goodsreceipt.dto.request.*;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptSummaryCountResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptBatchAllocationResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptExportResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptListItemResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptLineResponse;
import org.hvtra.iventoryservice.goodsreceipt.dto.response.GoodsReceiptResponse;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentBatchAllocationRow;
import org.hvtra.iventoryservice.goodsreceipt.repository.projection.GoodsReceiptExportRow;
import org.hvtra.iventoryservice.goodsreceipt.repository.projection.GoodsReceiptListItemRow;
import org.hvtra.iventoryservice.goodsreceipt.repository.projection.GoodsReceiptRow;
import org.hvtra.iventoryservice.document.repository.projection.InventoryDocumentLineRow;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.hvtra.iventoryservice.goodsreceipt.mapper.GoodsReceiptMapper;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentBatchAllocationRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentLineRepository;
import org.hvtra.iventoryservice.goodsreceipt.repository.GoodsReceiptRepository;
import org.hvtra.iventoryservice.goodsreceipt.service.GoodsReceiptService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.document.GoodsReceipt;
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
@RequiredArgsConstructor
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryDocumentLineRepository lineRepository;
    private final InventoryDocumentBatchAllocationRepository allocationRepository;
    private final CatalogClient catalogClient;
    private final IdentityClient identityClient;
    private final GoodsReceiptMapper mapper;
    private final SupplyLeadTimeService supplyLeadTimeService;

    private static final int MAX_EXPORT_ROWS = 10_000;

    @Override
    public PageResponse<GoodsReceiptListItemResponse> findAll(
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        String statusParam = status != null ? status.name() : null;
        String queryPattern = toQueryPattern(query);

        long totalElements = goodsReceiptRepository.countMatching(
                statusParam, locationId, supplierId, fromDate, toDate, queryPattern);

        List<GoodsReceiptListItemRow> rows = totalElements == 0
                ? List.of()
                : goodsReceiptRepository.searchListItems(
                        statusParam, locationId, supplierId, fromDate, toDate, queryPattern,
                        pageable.getPageSize(), pageable.getOffset());

        Map<UUID, String> supplierNameMap = fetchSupplierNames(distinctUuids(
                rows.stream().map(GoodsReceiptListItemRow::getSupplierId)));

        List<GoodsReceiptListItemResponse> content = rows.stream()
                .map(row -> {
                    UUID rowSupplierId = parseUuid(row.getSupplierId());
                    return new GoodsReceiptListItemResponse(
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

        Page<GoodsReceiptListItemResponse> page = new PageImpl<>(content, pageable, totalElements);
        return PageResponse.from(page);
    }

    @Override
    public List<GoodsReceiptExportResponse> export(
            InventoryDocumentStatus status,
            UUID locationId,
            UUID supplierId,
            String query,
            Instant fromDate,
            Instant toDate
    ) {
        List<GoodsReceiptExportRow> rows = goodsReceiptRepository.searchExportRows(
                status != null ? status.name() : null,
                locationId, supplierId, fromDate, toDate, toQueryPattern(query),
                MAX_EXPORT_ROWS);

        Map<UUID, String> supplierNameMap = fetchSupplierNames(distinctUuids(
                rows.stream().map(GoodsReceiptExportRow::getSupplierId)));
        Map<UUID, String> userNameMap = fetchUserNames(distinctUuids(
                rows.stream().map(GoodsReceiptExportRow::getCreatedBy)));

        return rows.stream()
                .map(row -> {
                    UUID rowSupplierId = parseUuid(row.getSupplierId());
                    UUID createdBy = parseUuid(row.getCreatedBy());
                    return new GoodsReceiptExportResponse(
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

    private GoodsReceiptResponse buildDetailResponse(GoodsReceiptRow header) {
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
                header.getReceivedBy(), header.getQcBy(), header.getPostedBy(),
                header.getExceptionApprovedBy())));

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

    private GoodsReceiptResponse toDocumentResponse(
            GoodsReceiptRow header,
            List<InventoryDocumentLineRow> lineRows,
            Map<String, List<InventoryDocumentBatchAllocationRow>> allocationsByLine,
            String supplierName,
            Map<UUID, String> userNameMap
    ) {
        List<GoodsReceiptLineResponse> lines = new ArrayList<>(lineRows.size());
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalAccepted = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;

        for (InventoryDocumentLineRow line : lineRows) {
            List<GoodsReceiptBatchAllocationResponse> allocations = allocationsByLine
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
            lines.add(new GoodsReceiptLineResponse(
                    parseUuid(line.getId()),
                    line.getLineNo(),
                    parseUuid(line.getInventoryItemId()),
                    line.getItemSku(),
                    itemName,
                    line.getItemType() != null ? InventoryItemType.valueOf(line.getItemType()) : null,
                    parseUuid(line.getUnitId()),
                    null,
                    ordered,
                    received,
                    nvl(line.getShortageQuantity()),
                    nvl(line.getExcessQuantity()),
                    accepted,
                    rejected,
                    line.getNote(),
                    allocations,
                    line.getExcessResolution() != null ? ReceiptExcessResolution.valueOf(line.getExcessResolution()) : null,
                    line.getShortageResolution() != null ? ReceiptShortageResolution.valueOf(line.getShortageResolution()) : null,
                    nvl(line.getReturnedQuantity()),
                    line.getPostedQuantity(),
                    line.getResolutionNote()
            ));
        }

        return new GoodsReceiptResponse(
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
                totalRejected,
                parseUuid(header.getExceptionApprovedBy()),
                userNameOf(userNameMap, header.getExceptionApprovedBy()),
                header.getExceptionApprovedAt(),
                header.getExceptionApprovalNote()
        );
    }

    private GoodsReceiptBatchAllocationResponse toAllocationResponse(InventoryDocumentBatchAllocationRow row) {
        return new GoodsReceiptBatchAllocationResponse(
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
    public GoodsReceiptSummaryCountResponse getSummaryCounts() {
        List<Object[]> rows = goodsReceiptRepository.countGroupByStatus();

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

    private GoodsReceiptResponse toEnrichedResponse(InventoryDocument document) {
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
                        document.getReceivedBy(), document.getQcBy(), document.getPostedBy(),
                        document.getExceptionApprovedBy())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return mapper.toResponse(document, supplierName, userNameMap);
    }

    @Override
    public GoodsReceiptResponse findById(UUID id) {
        GoodsReceiptRow header = goodsReceiptRepository.findRowById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GoodsReceipt", id));
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
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        InventoryLocation location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", request.locationId()));

        GoodsReceipt document = new GoodsReceipt();
        document.setDocumentCode(generateDocumentCode(InventoryDocumentType.GOODS_RECEIPT));
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

        GoodsReceipt saved = goodsReceiptRepository.save(document);
        return toEnrichedResponse(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse updateGoodsReceipt(UUID id, UpdateGoodsReceiptRequest request) {
        GoodsReceipt document = goodsReceiptRepository.findByIdWithDetails(id)
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
        goodsReceiptRepository.flush();
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

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse submitForApproval(UUID id) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT documents can be submitted for approval");
        }
        if (document.getLines().isEmpty()) {
            throw new BadRequestException("Cannot submit a document with no line items");
        }

        // Quản lý / Admin tự tạo và gửi phiếu thì không cần người khác duyệt: chuyển thẳng sang Đã duyệt
        UUID currentUserId = getCurrentUserId();
        boolean createdByManager = currentUserId != null && currentUserId.equals(document.getCreatedBy())
                && SecurityUtils.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER);
        if (createdByManager) {
            document.setStatus(InventoryDocumentStatus.APPROVED);
            document.setApprovedBy(currentUserId);
            document.setApprovedAt(Instant.now());
            document.setApprovalNote("Tự duyệt: người tạo phiếu là Quản lý / Admin");
        } else {
            document.setStatus(InventoryDocumentStatus.PENDING_APPROVAL);
        }
        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse approve(UUID id, ApproveReceiptRequest request) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL documents can be approved");
        }

        document.setStatus(InventoryDocumentStatus.APPROVED);
        document.setApprovedBy(getCurrentUserId());
        document.setApprovedAt(Instant.now());
        if (request != null && request.approvalNote() != null) {
            document.setApprovalNote(request.approvalNote());
        }

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse reject(UUID id, RejectReceiptRequest request) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL documents can be rejected");
        }

        document.setStatus(InventoryDocumentStatus.REJECTED);
        document.setRejectedBy(getCurrentUserId());
        document.setRejectedAt(Instant.now());
        document.setRejectionReason(request.rejectionReason());

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse startReceiving(UUID id) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED documents can start receiving");
        }

        document.setStatus(InventoryDocumentStatus.RECEIVING);
        document.setReceivedBy(getCurrentUserId());
        document.setReceivedAt(Instant.now());

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse confirmReceiving(UUID id, ReceiveReceiptRequest request) {
        GoodsReceipt document = getDocument(id);

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

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse completeQc(UUID id, QcReceiptRequest request) {
        GoodsReceipt document = getDocument(id);

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
            // Nguyên liệu không theo dõi hạn sử dụng: bỏ qua HSD gửi lên khi tạo lô
            boolean tracksExpiry = line.getInventoryItem().getItemType() != InventoryItemType.INGREDIENT;
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
                                newBatch.setExpiryDate(tracksExpiry ? batchInput.expiryDate() : null);
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

        GoodsReceipt savedDocument = goodsReceiptRepository.save(document);

        // Auto post nếu QC pass (không có exception)
        if (nextStatus == InventoryDocumentStatus.POSTED) {
            performPost(savedDocument);
            savedDocument.setStatus(InventoryDocumentStatus.POSTED);
            savedDocument = goodsReceiptRepository.save(savedDocument);
            supplyLeadTimeService.onReceiptPosted(savedDocument);
        }

        return toEnrichedResponse(savedDocument);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse completeInspection(UUID id, CompleteInspectionRequest request) {
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
    public GoodsReceiptResponse post(UUID id, ResolveReceiptExceptionRequest request) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() != InventoryDocumentStatus.PENDING_EXCEPTION_APPROVAL) {
            throw ErrorCode.INVENTORY_DOCUMENT_NOT_PENDING_EXCEPTION.exception();
        }

        Map<UUID, ResolveReceiptExceptionRequest.LineResolution> resolutionsByLine = new HashMap<>();
        if (request != null && request.lines() != null) {
            request.lines().forEach(resolution -> resolutionsByLine.put(resolution.lineId(), resolution));
        }

        for (InventoryDocumentLine line : document.getLines()) {
            applyExceptionResolution(line, resolutionsByLine.get(line.getId()));
        }

        String approvalNote = request != null ? request.approvalNote() : null;
        document.setExceptionApprovedBy(getCurrentUserId());
        document.setExceptionApprovedAt(Instant.now());
        document.setExceptionApprovalNote(approvalNote != null && !approvalNote.isBlank() ? approvalNote.trim() : null);

        performPost(document);
        document.setStatus(InventoryDocumentStatus.POSTED);

        GoodsReceipt posted = goodsReceiptRepository.save(document);
        supplyLeadTimeService.onReceiptPosted(posted);
        return toEnrichedResponse(posted);
    }

    /**
     * Chốt số ghi sổ của 1 dòng theo cách xử lý ngoại lệ được chọn. Phần không đạt QC (rejected) không bao giờ
     * vào kho (hủy); phần đạt QC mà không nhập (thừa bị từ chối / từ chối cả dòng) ghi vào returnedQuantity.
     */
    private void applyExceptionResolution(InventoryDocumentLine line, ResolveReceiptExceptionRequest.LineResolution resolution) {
        BigDecimal ordered = nvl(line.getOrderedQuantity());
        BigDecimal received = nvl(line.getReceivedQuantity());
        BigDecimal accepted = nvl(line.getAcceptedQuantity());
        String itemName = line.getInventoryItem().getName() != null
                ? line.getInventoryItem().getName()
                : line.getInventoryItem().getReferenceCode();

        BigDecimal posted = accepted;
        line.setExcessResolution(null);
        line.setShortageResolution(null);

        if (received.compareTo(ordered) > 0) {
            if (resolution == null || resolution.excessResolution() == null) {
                throw ErrorCode.RECEIPT_EXCESS_RESOLUTION_REQUIRED.exception(line.getLineNo(), itemName);
            }
            line.setExcessResolution(resolution.excessResolution());
            if (resolution.excessResolution() == ReceiptExcessResolution.ACCEPT_ORDERED_ONLY) {
                posted = accepted.min(ordered);
            }
        } else if (received.compareTo(ordered) < 0) {
            if (resolution == null || resolution.shortageResolution() == null) {
                throw ErrorCode.RECEIPT_SHORTAGE_RESOLUTION_REQUIRED.exception(line.getLineNo(), itemName);
            }
            line.setShortageResolution(resolution.shortageResolution());
            if (resolution.shortageResolution() == ReceiptShortageResolution.REJECT_LINE) {
                posted = BigDecimal.ZERO;
            }
        }

        line.setPostedQuantity(posted);
        line.setReturnedQuantity(accepted.subtract(posted));
        line.setResolutionNote(resolution != null && resolution.note() != null && !resolution.note().isBlank()
                ? resolution.note().trim()
                : null);
        trimBatchAllocations(line, posted);
    }

    /** Giảm phân bổ lô cho khớp số ghi sổ; lô không còn số lượng thì bỏ phân bổ (hàng đó không vào kho). */
    private void trimBatchAllocations(InventoryDocumentLine line, BigDecimal posted) {
        BigDecimal toRemove = line.getBatchAllocations().stream()
                .map(InventoryDocumentBatchAllocation::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(posted);
        if (toRemove.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<InventoryDocumentBatchAllocation> allocations = line.getBatchAllocations().stream()
                .sorted(Comparator.comparing((InventoryDocumentBatchAllocation a) -> a.getInventoryBatch().getBatchCode()).reversed())
                .toList();
        for (InventoryDocumentBatchAllocation allocation : allocations) {
            if (toRemove.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal reduce = allocation.getQuantity().min(toRemove);
            allocation.setQuantity(allocation.getQuantity().subtract(reduce));
            toRemove = toRemove.subtract(reduce);
            if (allocation.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                line.getBatchAllocations().remove(allocation);
            }
        }
    }

    @Override
    @Transactional
    public GoodsReceiptResponse cancel(UUID id, String note) {
        GoodsReceipt document = getDocument(id);

        if (document.getStatus() == InventoryDocumentStatus.POSTED) {
            throw new BadRequestException("Cannot cancel an already POSTED document");
        }

        document.setStatus(InventoryDocumentStatus.CANCELLED);
        if (note != null && !note.isBlank()) {
            document.setNote((document.getNote() != null ? document.getNote() + " | " : "") + "Hủy phiếu: " + note);
        }

        return toEnrichedResponse(goodsReceiptRepository.save(document));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        GoodsReceipt document = goodsReceiptRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ErrorCode.INVENTORY_DOCUMENT_NOT_FOUND.exception());

        if (!isAdminOrCreator(document)) {
            throw ErrorCode.INVENTORY_DOCUMENT_DELETE_FORBIDDEN.exception();
        }

        if (document.getStatus() != InventoryDocumentStatus.DRAFT) {
            throw ErrorCode.INVENTORY_DOCUMENT_NOT_DELETABLE.exception();
        }

        document.setIsDeleted(true);
        goodsReceiptRepository.save(document);
    }

    private void performPost(InventoryDocument document) {
        InventoryLocation location = locationRepository.findById(document.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryLocation", document.getLocationId()));

        UUID branchId = document.getBranchId() != null ? document.getBranchId() : location.getBranchId();

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(InventoryTransactionType.STOCK_IN);
        transaction.setReferenceType(InventoryReferenceType.GOODS_RECEIPT);
        transaction.setReferenceId(document.getId().toString());
        transaction.setBranchId(branchId != null ? branchId : location.getId());
        transaction.setInventoryDocument(document);

        for (InventoryDocumentLine docLine : document.getLines()) {
            if (docLine.getPostedQuantity() == null) {
                docLine.setPostedQuantity(nvl(docLine.getAcceptedQuantity()));
            }
            BigDecimal postedQuantity = docLine.getPostedQuantity();
            if (postedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
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
                BigDecimal after = before.add(postedQuantity);
                balance.setQuantityOnHand(after);
                balanceRepository.save(balance);

                InventoryTransactionLine transLine = new InventoryTransactionLine();
                transLine.setInventoryItem(item);
                transLine.setLocation(location);
                transLine.setQuantity(postedQuantity);
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
        if (SecurityUtils.isSuperAdmin()) {
            return true;
        }
        UUID currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(document.getCreatedBy());
    }

    private GoodsReceipt getDocument(UUID id) {
        return goodsReceiptRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("GoodsReceipt", id));
    }

    private String generateDocumentCode(InventoryDocumentType type) {
        String prefix = type == InventoryDocumentType.GOODS_RECEIPT ? "NK" : "DOC";
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
