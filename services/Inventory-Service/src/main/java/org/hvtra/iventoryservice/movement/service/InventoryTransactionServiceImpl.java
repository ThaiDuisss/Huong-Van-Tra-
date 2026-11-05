package org.hvtra.iventoryservice.movement.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hvtra.iventoryservice.domain.entity.*;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.movement.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.movement.dto.request.StockInRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreStockAdjustmentRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreStockInRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreStockOutRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreTransferRequest;
import org.hvtra.iventoryservice.movement.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.movement.mapper.InventoryTransactionMapper;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryTransactionServiceImpl implements InventoryTransactionService {
    private static final Logger log = LoggerFactory.getLogger(InventoryTransactionServiceImpl.class);
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionMapper mapper;
    private final IdentityClient identityClient;

    public InventoryTransactionServiceImpl(
            InventoryTransactionRepository transactionRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryBatchRepository batchRepository,
            InventoryTransactionMapper mapper) {
        this(transactionRepository, itemRepository, locationRepository, balanceRepository, batchRepository, mapper, null);
    }

    @Autowired
    public InventoryTransactionServiceImpl(
            InventoryTransactionRepository transactionRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryBatchRepository batchRepository,
            InventoryTransactionMapper mapper,
            @Autowired(required = false) IdentityClient identityClient) {
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.batchRepository = batchRepository;
        this.mapper = mapper;
        this.identityClient = identityClient;
    }

    @Override
    @Transactional
    public InventoryTransactionResponse stockIn(StockInRequest request) {
        validateDistinctStockInLines(request.lines());
        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.STOCK_IN,
                request.branchId(),
                null,
                request.referenceType(),
                request.referenceId());

        for (StockInRequest.LineRequest requestLine : request.lines()) {
            InventoryItem item = activeItemForUpdate(requestLine.inventoryItemId());
            InventoryLocation location = activeLocation(requestLine.locationId(), request.branchId());
            InventoryBalance balance = balanceRepository
                    .findForUpdate(item.getId(), location.getId())
                    .orElseGet(() -> newBalance(item, location));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = before.add(requestLine.quantity());
            validateNonNegative(after, "quantityOnHand");
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);
            transaction.addLine(createLine(
                    item, location, requestLine.quantity(), before, after));
        }
        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    @Override
    @Transactional
    public InventoryTransactionResponse adjust(StockAdjustmentRequest request) {
        validateDistinctAdjustmentLines(request.lines());
        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.ADJUSTMENT,
                request.branchId(),
                null,
                request.referenceType(),
                request.referenceId());

        for (StockAdjustmentRequest.LineRequest requestLine : request.lines()) {
            InventoryItem item = activeItemForUpdate(requestLine.inventoryItemId());
            InventoryLocation location = activeLocation(requestLine.locationId(), request.branchId());
            InventoryBalance balance = balanceRepository
                    .findForUpdate(item.getId(), location.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Inventory balance does not exist for item "
                                    + item.getId() + " at location " + location.getId()));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = requestLine.actualQuantity();
            validateNonNegative(after, "actualQuantity");
            BigDecimal difference = after.subtract(before);
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);
            transaction.addLine(createLine(item, location, difference, before, after));
        }
        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    @Override
    public PageResponse<InventoryTransactionResponse> findAll(
            String transactionCode,
            InventoryTransactionType type,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String createdBy,
            UUID branchId,
            InventoryReferenceType referenceType,
            String referenceId,
            Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadRequestException("toDate must be greater than or equal to fromDate");
        }
        Specification<InventoryTransaction> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (transactionCode != null && !transactionCode.isBlank()) {
                String searchTrim = transactionCode.trim().toLowerCase();
                var searchPredicates = new ArrayList<Predicate>();
                searchPredicates.add(cb.like(
                        cb.lower(root.get("transactionCode")),
                        "%" + searchTrim + "%"));
                searchPredicates.add(cb.like(
                        cb.lower(root.get("referenceId")),
                        "%" + searchTrim + "%"));
                try {
                    searchPredicates.add(cb.equal(root.get("createdBy"), UUID.fromString(searchTrim)));
                } catch (IllegalArgumentException e) {
                    // Ignored: search query is not a valid UUID format
                }
                predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
            }
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            if (createdBy != null) predicates.add(cb.equal(root.get("createdBy"), createdBy));
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (referenceType != null) {
                predicates.add(cb.equal(root.get("referenceType"), referenceType));
            }
            if (referenceId != null) predicates.add(cb.equal(root.get("referenceId"), referenceId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return toPageResponse(transactionRepository.findAll(specification, pageable));
    }

    @Override
    public InventoryTransactionResponse findById(UUID id) {
        InventoryTransaction transaction = transactionRepository.findByIdWithLines(id)
                .or(() -> transactionRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction", id));

        Map<UUID, String> userNames = transaction.getCreatedBy() != null
                ? fetchUserNames(List.of(transaction.getCreatedBy()))
                : Map.of();
        Map<UUID, String> branchNames = transaction.getBranchId() != null
                ? fetchBranchNames(List.of(transaction.getBranchId()))
                : Map.of();

        return mapper.toResponse(transaction, userNames, branchNames);
    }

    @Override
    public PageResponse<InventoryTransactionResponse> findAllForStore(
            String transactionCode,
            InventoryTransactionType type,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String createdBy,
            InventoryReferenceType referenceType,
            String referenceId,
            Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadRequestException("toDate must be greater than or equal to fromDate");
        }

        // Store = các kệ SHELF thuộc WAREHOUSE ACTIVE đầu tiên
        UUID storeWarehouseId = locationRepository
                .findFirstByTypeAndStatusOrderByCreatedAtAsc(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                .map(InventoryLocation::getId)
                .orElse(null);
        if (storeWarehouseId == null) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true, true);
        }

        Specification<InventoryTransaction> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // EXISTS thay cho IN (toàn bộ id) — DB tự lọc, không phải kéo hết id về app
            Subquery<Integer> storeLine = query.subquery(Integer.class);
            Root<InventoryTransactionLine> line = storeLine.from(InventoryTransactionLine.class);
            Join<InventoryTransactionLine, InventoryLocation> lineLocation = line.join("location");
            storeLine.select(cb.literal(1)).where(
                    cb.equal(line.get("transaction"), root),
                    cb.equal(lineLocation.get("type"), InventoryLocationType.SHELF),
                    cb.equal(lineLocation.get("parentLocation").get("id"), storeWarehouseId));
            predicates.add(cb.exists(storeLine));

            if (transactionCode != null && !transactionCode.isBlank()) {
                String searchTrim = transactionCode.trim().toLowerCase();
                var searchPredicates = new ArrayList<Predicate>();
                searchPredicates.add(cb.like(cb.lower(root.get("transactionCode")), "%" + searchTrim + "%"));
                searchPredicates.add(cb.like(cb.lower(root.get("referenceId")), "%" + searchTrim + "%"));
                try {
                    searchPredicates.add(cb.equal(root.get("createdBy"), UUID.fromString(searchTrim)));
                } catch (IllegalArgumentException e) {
                    // Ignored: not a valid UUID
                }
                predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
            }
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            if (toDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            if (createdBy != null) predicates.add(cb.equal(root.get("createdBy"), createdBy));
            if (referenceType != null) predicates.add(cb.equal(root.get("referenceType"), referenceType));
            if (referenceId != null) predicates.add(cb.equal(root.get("referenceId"), referenceId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return toPageResponse(transactionRepository.findAll(specification, pageable));
    }

    /**
     * Map 1 trang transaction: nạp lines của cả trang bằng 1 query rồi giữ nguyên thứ tự đã phân trang.
     */
    private PageResponse<InventoryTransactionResponse> toPageResponse(Page<InventoryTransaction> page) {
        List<UUID> ids = page.getContent().stream().map(InventoryTransaction::getId).toList();
        Map<UUID, InventoryTransaction> withLines = ids.isEmpty()
                ? Map.of()
                : transactionRepository.findAllWithLinesByIdIn(ids).stream()
                        .collect(Collectors.toMap(InventoryTransaction::getId, Function.identity()));

        Set<UUID> userIds = page.getContent().stream()
                .map(InventoryTransaction::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> userNames = fetchUserNames(userIds);

        Set<UUID> branchIds = page.getContent().stream()
                .map(InventoryTransaction::getBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> branchNames = fetchBranchNames(branchIds);

        return PageResponse.from(page.map(t -> {
            InventoryTransaction tx = withLines.getOrDefault(t.getId(), t);
            return mapper.toResponse(tx, userNames, branchNames);
        }));
    }

    private Map<UUID, String> fetchUserNames(Collection<UUID> userIds) {
        Map<UUID, String> userNameMap = new HashMap<>();
        if (identityClient == null || userIds == null || userIds.isEmpty()) {
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
        } catch (Exception exception) {
            log.warn("Không lấy được tên người dùng từ Identity-Service: {}", exception.getMessage());
        }
        return userNameMap;
    }

    private Map<UUID, String> fetchBranchNames(Collection<UUID> branchIds) {
        Map<UUID, String> branchNames = new HashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return branchNames;
        }
        try {
            List<InventoryLocation> warehouses = locationRepository.findAll((root, query, cb) -> cb.and(
                    root.get("branchId").in(branchIds),
                    cb.equal(root.get("type"), InventoryLocationType.WAREHOUSE),
                    cb.equal(root.get("status"), InventoryStatus.ACTIVE)
            ));
            for (InventoryLocation w : warehouses) {
                branchNames.put(w.getBranchId(), w.getName());
            }
        } catch (Exception exception) {
            log.warn("Không lấy được tên chi nhánh: {}", exception.getMessage());
        }
        return branchNames;
    }

    private UUID tryParseUuid(String value) {
        try {
            return value != null && !value.isBlank() ? UUID.fromString(value.trim()) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private InventoryTransaction createTransaction(
            InventoryTransactionType type,
            UUID branchId,
            UUID createdBy,
            InventoryReferenceType referenceType,
            String referenceId) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateCode(type));
        transaction.setType(type);
        transaction.setBranchId(branchId);
        transaction.setCreatedBy(createdBy);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        return transaction;
    }

    private InventoryTransactionLine createLine(
            InventoryItem item,
            InventoryLocation location,
            BigDecimal quantity,
            BigDecimal before,
            BigDecimal after) {
        InventoryTransactionLine line = new InventoryTransactionLine();
        line.setInventoryItem(item);
        line.setLocation(location);
        line.setQuantity(quantity);
        line.setBeforeQuantity(before);
        line.setAfterQuantity(after);
        return line;
    }

    private InventoryBalance newBalance(InventoryItem item, InventoryLocation location) {
        InventoryBatch batch = batchRepository
                .findFirstByInventoryItemId(item.getId())
                .orElseGet(() -> {
                    InventoryBatch newBatch = new InventoryBatch();
                    newBatch.setInventoryItem(item);
                    newBatch.setBatchCode("DEFAULT-" + item.getId().toString().substring(0, 8).toUpperCase());
                    newBatch.setStatus(InventoryBatchStatus.AVAILABLE);
                    return batchRepository.saveAndFlush(newBatch);
                });

        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(BigDecimal.ZERO);
        balance.setQuantityReserved(BigDecimal.ZERO);
        balance.setQuantityBlocked(BigDecimal.ZERO);
        return balance;
    }

    private InventoryItem activeItemForUpdate(UUID id) {
        InventoryItem item = itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
        if (item.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory item is inactive: " + id);
        }
        return item;
    }

    private InventoryLocation activeLocation(UUID id, UUID branchId) {
        InventoryLocation location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory location", id));
        if (location.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory location is inactive: " + id);
        }
        if (!location.getBranchId().equals(branchId)) {
            throw new BadRequestException(
                    "Inventory location " + id + " does not belong to branch " + branchId);
        }
        return location;
    }

    private void validateDistinctStockInLines(List<StockInRequest.LineRequest> lines) {
        Set<String> keys = new HashSet<>();
        for (StockInRequest.LineRequest line : lines) {
            validateMovementKey(keys, line.inventoryItemId(), line.locationId());
        }
    }

    private void validateDistinctAdjustmentLines(
            List<StockAdjustmentRequest.LineRequest> lines) {
        Set<String> keys = new HashSet<>();
        for (StockAdjustmentRequest.LineRequest line : lines) {
            validateMovementKey(keys, line.inventoryItemId(), line.locationId());
        }
    }

    private void validateMovementKey(Set<String> keys, UUID itemId, UUID locationId) {
        String key = itemId + ":" + locationId;
        if (!keys.add(key)) {
            throw new BadRequestException(
                    "Duplicate inventory item and location in transaction: " + key);
        }
    }

    private void validateNonNegative(BigDecimal quantity, String field) {
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(field + " cannot be negative");
        }
    }

    private void validateSaleableLocation(InventoryLocation location) {
        if (!InventoryLocationSaleability.isSaleable(location)) {
            throw new BadRequestException("Inventory location is not saleable");
        }
    }

    /**
     * Resolve và validate một kệ (SHELF) đang ACTIVE.
     * Dùng cho StoreStockIn, StoreAdjust, StoreTransfer.
     */
    private InventoryLocation resolveShelfLocation(UUID shelfId) {
        InventoryLocation loc = locationRepository.findById(shelfId)
                .orElseThrow(() -> new ResourceNotFoundException("Kệ", shelfId));
        if (loc.getType() != InventoryLocationType.SHELF) {
            throw new BadRequestException("Vị trí " + shelfId + " không phải là Kệ (SHELF)");
        }
        if (loc.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Kệ " + shelfId + " hiện không hoạt động (INACTIVE)");
        }
        validateSaleableLocation(loc);
        return loc;
    }

    /**
     * Resolve kho WAREHOUSE đầu tiên đang ACTIVE.
     * Vẫn dùng cho storeStockOut (xuất hàng POS).
     */
    private InventoryLocation resolveWarehouseLocation() {
        InventoryLocation location = locationRepository.findFirstByTypeAndStatus(
                        InventoryLocationType.WAREHOUSE,
                        InventoryStatus.ACTIVE
                )
                .orElseThrow(() -> new BadRequestException(
                        "No active WAREHOUSE location found"));
        validateSaleableLocation(location);
        return location;
    }

    @Override
    @Transactional
    public InventoryTransactionResponse storeStockIn(StoreStockInRequest request) {
        InventoryLocation shelf = resolveShelfLocation(request.shelfId());

        validateDistinctStoreStockInLines(request.lines());

        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.STOCK_IN,
                shelf.getBranchId(),
                null,
                request.referenceType(),
                request.referenceId());

        for (StoreStockInRequest.LineRequest requestLine : request.lines()) {
            InventoryItem item = activeItemForUpdate(requestLine.inventoryItemId());
            // SELECT FOR UPDATE – đảm bảo không race condition
            InventoryBalance balance = balanceRepository
                    .findForUpdate(item.getId(), shelf.getId())
                    .orElseGet(() -> newBalance(item, shelf));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = before.add(requestLine.quantity());
            validateNonNegative(after, "quantityOnHand");
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);
            transaction.addLine(createLine(
                    item, shelf, requestLine.quantity(), before, after));
        }
        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    @Override
    @Transactional
    public InventoryTransactionResponse storeAdjust(StoreStockAdjustmentRequest request) {
        InventoryLocation shelf = resolveShelfLocation(request.shelfId());

        validateDistinctStoreAdjustmentLines(request.lines());

        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.ADJUSTMENT,
                shelf.getBranchId(),
                null,
                request.referenceType(),
                request.referenceId());

        for (StoreStockAdjustmentRequest.LineRequest requestLine : request.lines()) {
            InventoryItem item = activeItemForUpdate(requestLine.inventoryItemId());
            // SELECT FOR UPDATE – đảm bảo không race condition
            InventoryBalance balance = balanceRepository
                    .findForUpdate(item.getId(), shelf.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Inventory balance does not exist for item "
                                    + item.getId() + " at shelf " + shelf.getId()));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal after = requestLine.actualQuantity();
            validateNonNegative(after, "actualQuantity");
            BigDecimal difference = after.subtract(before);
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);
            transaction.addLine(createLine(item, shelf, difference, before, after));
        }
        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    @Override
    @Transactional
    public InventoryTransactionResponse storeTransfer(StoreTransferRequest request) {
        // Validate kệ nguồn ≠ kệ đích
        if (request.fromShelfId().equals(request.toShelfId())) {
            throw new BadRequestException("Kệ nguồn và kệ đích phải khác nhau");
        }

        InventoryLocation fromShelf = resolveShelfLocation(request.fromShelfId());
        InventoryLocation toShelf   = resolveShelfLocation(request.toShelfId());
        InventoryItem item          = activeItemForUpdate(request.inventoryItemId());

        // SELECT FOR UPDATE trên cả hai kệ (thứ tự theo UUID để tránh deadlock)
        UUID first  = request.fromShelfId().compareTo(request.toShelfId()) < 0
                ? request.fromShelfId() : request.toShelfId();
        UUID second = first.equals(request.fromShelfId())
                ? request.toShelfId() : request.fromShelfId();

        // Acquire locks theo thứ tự nhất quán
        balanceRepository.findForUpdate(item.getId(), first);
        balanceRepository.findForUpdate(item.getId(), second);

        // Re-read with lock đã được acquired ở trên
        InventoryBalance fromBalance = balanceRepository
                .findForUpdate(item.getId(), fromShelf.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Không có tồn kho của sản phẩm " + item.getId()
                                + " tại kệ nguồn " + fromShelf.getId()));

        // Validate số lượng đủ để chuyển
        BigDecimal available = fromBalance.getAvailableQuantity();
        if (available.compareTo(request.quantity()) < 0) {
            throw new BadRequestException(
                    "Số lượng tồn kho tại kệ nguồn ("
                            + available + ") không đủ để chuyển " + request.quantity());
        }

        // Cập nhật kệ nguồn (giữ record dù về 0)
        BigDecimal fromBefore = fromBalance.getQuantityOnHand();
        BigDecimal fromAfter  = fromBefore.subtract(request.quantity());
        fromBalance.setQuantityOnHand(fromAfter);
        balanceRepository.save(fromBalance);

        // Cập nhật kệ đích (tạo mới nếu chưa có)
        InventoryBalance toBalance = balanceRepository
                .findForUpdate(item.getId(), toShelf.getId())
                .orElseGet(() -> newBalance(item, toShelf));
        BigDecimal toBefore = toBalance.getQuantityOnHand();
        BigDecimal toAfter  = toBefore.add(request.quantity());
        toBalance.setQuantityOnHand(toAfter);
        balanceRepository.save(toBalance);

        // Tạo 1 transaction TRANSFER với 2 lines
        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.TRANSFER,
                fromShelf.getBranchId(),
                null,
                InventoryReferenceType.STOCK_TRANSFER,
                request.note());

        // Line 1: trừ từ kệ nguồn (quantity âm)
        transaction.addLine(createLine(
                item, fromShelf, request.quantity().negate(), fromBefore, fromAfter));
        // Line 2: cộng vào kệ đích (quantity dương)
        transaction.addLine(createLine(
                item, toShelf, request.quantity(), toBefore, toAfter));

        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    @Override
    @Transactional
    public InventoryTransactionResponse storeStockOut(StoreStockOutRequest request) {
        InventoryLocation location = resolveWarehouseLocation();

        validateDistinctStoreStockOutLines(request.lines());

        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.SALE_OUT,
                location.getBranchId(),
                null,
                request.referenceType(),
                request.referenceId());

        for (StoreStockOutRequest.LineRequest requestLine : request.lines()) {
            InventoryItem item = activeItemForUpdate(requestLine.inventoryItemId());
            InventoryBalance balance = balanceRepository
                    .findForUpdate(item.getId(), location.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Inventory balance does not exist for item "
                                    + item.getId() + " at location " + location.getId()));

            BigDecimal before = balance.getQuantityOnHand();
            BigDecimal available = balance.getAvailableQuantity();
            if (available.compareTo(requestLine.quantity()) < 0) {
                throw new BadRequestException(
                        "Saleable stock is not enough for item " + item.getId()
                                + " at location " + location.getId());
            }
            BigDecimal after = before.subtract(requestLine.quantity());
            validateNonNegative(after, "quantityOnHand");
            balance.setQuantityOnHand(after);
            balanceRepository.save(balance);
            transaction.addLine(createLine(
                    item, location, requestLine.quantity().negate(), before, after));
        }
        return mapper.toResponse(transactionRepository.saveAndFlush(transaction));
    }

    private void validateDistinctStoreStockOutLines(List<StoreStockOutRequest.LineRequest> lines) {
        Set<UUID> itemIds = new HashSet<>();
        for (StoreStockOutRequest.LineRequest line : lines) {
            if (!itemIds.add(line.inventoryItemId())) {
                throw new BadRequestException("Duplicate inventory item in transaction: " + line.inventoryItemId());
            }
        }
    }

    private void validateDistinctStoreStockInLines(List<StoreStockInRequest.LineRequest> lines) {
        Set<UUID> itemIds = new HashSet<>();
        for (StoreStockInRequest.LineRequest line : lines) {
            if (!itemIds.add(line.inventoryItemId())) {
                throw new BadRequestException("Duplicate inventory item in transaction: " + line.inventoryItemId());
            }
        }
    }

    private void validateDistinctStoreAdjustmentLines(
            List<StoreStockAdjustmentRequest.LineRequest> lines) {
        Set<UUID> itemIds = new HashSet<>();
        for (StoreStockAdjustmentRequest.LineRequest line : lines) {
            if (!itemIds.add(line.inventoryItemId())) {
                throw new BadRequestException("Duplicate inventory item in transaction: " + line.inventoryItemId());
            }
        }
    }

    private String generateCode(InventoryTransactionType type) {
        String prefix = switch (type) {
            case STOCK_IN -> "SIN";
            case SALE_OUT -> "SOUT";
            case RETURN_IN -> "RIN";
            case ADJUSTMENT -> "ADJ";
            case TRANSFER -> "TRF";
            case GOODS_RECEIPT -> null;
            case PRODUCTION_RECEIPT -> null;
            case CUSTOMER_RETURN_RECEIPT -> null;
            case PRODUCTION_ISSUE -> null;
            case CUSTOM_BUNDLE_ISSUE -> null;
            case TRANSFER_ISSUE -> null;
            case TRANSFER_RECEIPT -> null;
            case SUPPLIER_RETURN -> null;
            case ADJUSTMENT_IN -> null;
            case ADJUSTMENT_OUT -> null;
            case WRITE_OFF -> null;
        };
        return prefix + "-"
                + LocalDate.now().format(CODE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
