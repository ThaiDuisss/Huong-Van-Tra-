package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.entity.*;
import org.hvtra.iventoryservice.enums.*;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.InventoryTransactionMapper;
import org.hvtra.iventoryservice.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.service.InventoryTransactionService;
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
                predicates.add(cb.like(
                        cb.lower(root.get("transactionCode")),
                        "%" + transactionCode.trim().toLowerCase() + "%"));
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

    private void validateDistinctStockInLines(java.util.List<StockInRequest.LineRequest> lines) {
        Set<String> keys = new HashSet<>();
        for (StockInRequest.LineRequest line : lines) {
            validateMovementKey(keys, line.inventoryItemId(), line.locationId());
        }
    }

    private void validateDistinctAdjustmentLines(
            java.util.List<StockAdjustmentRequest.LineRequest> lines) {
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

    private String generateCode(InventoryTransactionType type) {
        String prefix = switch (type) {
            case STOCK_IN -> "SIN";
            case SALE_OUT -> "SOUT";
            case RETURN_IN -> "RIN";
            case ADJUSTMENT -> "ADJ";
            case TRANSFER -> "TRF";
        };
        return prefix + "-"
                + LocalDate.now().format(CODE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
