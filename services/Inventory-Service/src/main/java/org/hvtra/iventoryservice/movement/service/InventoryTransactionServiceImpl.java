package org.hvtra.iventoryservice.movement.service;

import jakarta.persistence.criteria.Predicate;
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
import org.hvtra.iventoryservice.movement.mapper.InventoryTransactionMapper;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InventoryTransactionServiceImpl implements InventoryTransactionService {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionMapper mapper;

    public InventoryTransactionServiceImpl(
            InventoryTransactionRepository transactionRepository,
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionMapper mapper) {
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.mapper = mapper;
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
        return PageResponse.from(
                transactionRepository.findAll(specification, pageable).map(mapper::toResponse));
    }

    @Override
    public InventoryTransactionResponse findById(UUID id) {
        InventoryTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory transaction", id));
        return mapper.toResponse(transaction);
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

        // Lấy danh sách ID transaction thuộc kệ SHELF của WAREHOUSE đầu tiên
        List<UUID> storeTransactionIds = transactionRepository.findStoreTransactionIds();

        Specification<InventoryTransaction> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // Chỉ lấy các transaction thuộc store (SHELF → parent WAREHOUSE đầu tiên)
            if (!storeTransactionIds.isEmpty()) {
                predicates.add(root.get("id").in(storeTransactionIds));
            } else {
                // Không có transaction nào thỏa điều kiện → trả về rỗng
                predicates.add(cb.disjunction());
            }

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
        return PageResponse.from(
                transactionRepository.findAll(specification, pageable).map(mapper::toResponse));
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
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setLocation(location);
        balance.setQuantityOnHand(BigDecimal.ZERO);
        balance.setQuantityReserved(BigDecimal.ZERO);
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
        return loc;
    }

    /**
     * Resolve kho WAREHOUSE đầu tiên đang ACTIVE.
     * Vẫn dùng cho storeStockOut (xuất hàng POS).
     */
    private InventoryLocation resolveWarehouseLocation() {
        return locationRepository.findFirstByTypeAndStatus(
                        InventoryLocationType.WAREHOUSE,
                        InventoryStatus.ACTIVE
                )
                .orElseThrow(() -> new BadRequestException(
                        "No active WAREHOUSE location found"));
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
        BigDecimal available = fromBalance.getQuantityOnHand();
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
            case PURCHASE_RECEIPT -> null;
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
