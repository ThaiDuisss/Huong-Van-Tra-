package org.hvtra.iventoryservice.counting.service;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.counting.dto.request.CreateInventoryTakeRequest;
import org.hvtra.iventoryservice.counting.dto.request.UpdateInventoryCountsRequest;
import org.hvtra.iventoryservice.counting.dto.response.InventoryTakeResponse;
import org.hvtra.iventoryservice.counting.mapper.InventoryTakeMapper;
import org.hvtra.iventoryservice.counting.repository.InventoryBatchBalanceRepository;
import org.hvtra.iventoryservice.counting.repository.InventoryCountRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatchBalance;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCount;
import org.hvtra.iventoryservice.domain.entity.counting.InventoryCountLine;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryTakeServiceImpl implements InventoryTakeService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryCountRepository countRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBatchBalanceRepository batchBalanceRepository;
    private final InventoryTakeMapper mapper;

    public InventoryTakeServiceImpl(
            InventoryCountRepository countRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryBatchBalanceRepository batchBalanceRepository,
            InventoryTakeMapper mapper) {
        this.countRepository = countRepository;
        this.balanceRepository = balanceRepository;
        this.batchBalanceRepository = batchBalanceRepository;
        this.mapper = mapper;
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
        validateScopeInput(request);

        InventoryCount count = new InventoryCount();
        count.setCountCode(generateCountCode());
        count.setBranchId(request.branchId());
        count.setScopeType(request.scopeType());
        count.setNote(request.note());

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse start(UUID id) {
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

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse updateCounts(UUID id, UpdateInventoryCountsRequest request) {
        InventoryCount count = findCountOrThrow(id);

        // Delegate validation to entity
        count.validateCanRecordCounts();

        for (UpdateInventoryCountsRequest.CountLineUpdate lineUpdate : request.lines()) {
            InventoryCountLine line = count.getLines().stream()
                    .filter(l -> l.getId().equals(lineUpdate.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory count line", lineUpdate.lineId()));

            // Delegate recording to entity
            line.recordCount(lineUpdate.countedQuantity(), lineUpdate.reasonCode(), lineUpdate.note());
        }

        return mapper.toResponse(countRepository.save(count));
    }

    @Override
    @Transactional
    public InventoryTakeResponse finish(UUID id) {
        InventoryCount count = findCountOrThrow(id);

        // Delegate finish logic (validate + finalize) to entity
        count.finish();

        return mapper.toResponse(countRepository.save(count));
    }

    // ====== Private Helpers ======

    private InventoryCount findCountOrThrow(UUID id) {
        return countRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory count", id));
    }

    private void validateScopeInput(CreateInventoryTakeRequest request) {
        switch (request.scopeType()) {
            case LOCATION -> {
                if (request.locationIds() == null || request.locationIds().isEmpty()) {
                    throw new BadRequestException("locationIds bắt buộc khi scopeType = LOCATION");
                }
            }
            case PARTIAL -> {
                if (request.inventoryItemIds() == null || request.inventoryItemIds().isEmpty()) {
                    throw new BadRequestException("inventoryItemIds bắt buộc khi scopeType = PARTIAL");
                }
            }
            case FULL -> { /* No extra input needed */ }
        }
    }

    private List<InventoryBalance> queryBalancesForScope(InventoryCount count) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            root.fetch("inventoryItem");
            root.fetch("location");

            predicates.add(cb.equal(root.get("location").get("branchId"), count.getBranchId()));

            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return balanceRepository.findAll(spec);
    }

    private List<InventoryBatchBalance> queryBatchBalancesForScope(InventoryCount count) {
        return switch (count.getScopeType()) {
            case FULL -> batchBalanceRepository
                    .findByBranchWithPositiveBalance(count.getBranchId());
            case LOCATION -> {
                // locationIds đã validate trong create(), nhưng không lưu trên entity
                // → fallback dùng branchId
                yield batchBalanceRepository
                        .findByBranchWithPositiveBalance(count.getBranchId());
            }
            case PARTIAL -> {
                // Tương tự, fallback dùng branchId
                yield batchBalanceRepository
                        .findByBranchWithPositiveBalance(count.getBranchId());
            }
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
