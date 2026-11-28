package org.hvtra.iventoryservice.disposal.service.impl;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.disposal.dto.request.CreateDamagedReturnDisposalRequest;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestLineResponse;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.disposal.service.DisposalRequestService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisposalRequestServiceImpl implements DisposalRequestService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DisposalRequestRepository requestRepository;
    private final CustomerReturnInspectionRepository inspectionRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final CustomerReturnInspectionService inspectionService;

    @Override
    public DisposalRequestResponse getRequest(UUID requestId) {
        return toResponse(requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Disposal request", requestId)));
    }

    @Override
    public Page<DisposalRequestResponse> listRequests(
            String keyword,
            String requestCode,
            String returnCode,
            DisposalRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        Specification<DisposalRequest> specification = matchingRequests(keyword, requestCode, returnCode, status, fromDate, toDate);
        Specification<DisposalRequest> priorityOrder = (root, query, builder) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                var priority = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("status"), DisposalRequestStatus.PENDING_APPROVAL), 0)
                        .when(builder.equal(root.get("status"), DisposalRequestStatus.APPROVED), 1)
                        .otherwise(2);
                query.orderBy(
                        builder.asc(priority),
                        builder.desc(root.get("createdAt")),
                        builder.asc(root.get("requestCode"))
                );
            }
            return builder.conjunction();
        };
        // Pageable sort would override the Specification order; pagination must use the global priority order.
        Pageable priorityPageable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : pageable;
        return requestRepository.findAll(specification.and(priorityOrder), priorityPageable).map(this::toResponse);
    }

    @Override
    public Map<String, Long> getStatusCounts(
            String keyword, String requestCode, String returnCode, LocalDate fromDate, LocalDate toDate) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", requestRepository.count(matchingRequests(keyword, requestCode, returnCode, null, fromDate, toDate)));
        counts.put("PENDING_APPROVAL", requestRepository.count(matchingRequests(
                keyword, requestCode, returnCode, DisposalRequestStatus.PENDING_APPROVAL, fromDate, toDate)));
        counts.put("APPROVED", requestRepository.count(matchingRequests(
                keyword, requestCode, returnCode, DisposalRequestStatus.APPROVED, fromDate, toDate)));
        return counts;
    }

    private Specification<DisposalRequest> matchingRequests(
            String keyword,
            String requestCode,
            String returnCode,
            DisposalRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        // Match the Order/Return API convention: inclusive dates in the JVM's local zone,
        // converted to an exclusive upper Instant boundary for the database query.
        ZoneId zone = ZoneId.systemDefault();
        Instant fromCreatedAt = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant toCreatedBefore = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("isDeleted")));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("requestCode")), pattern),
                        builder.like(builder.lower(root.get("returnCode")), pattern)
                ));
            }
            if (requestCode != null && !requestCode.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("requestCode")),
                        "%" + requestCode.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (returnCode != null && !returnCode.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("returnCode")),
                        "%" + returnCode.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (fromCreatedAt != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), fromCreatedAt));
            }
            if (toCreatedBefore != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), toCreatedBefore));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    @Transactional
    public DisposalRequestResponse approveRequest(UUID requestId) {
        requireApprovalRole();

        DisposalRequest snapshot = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Disposal request", requestId));
        UUID inspectionId = snapshot.getSourceInspection().getId();

        CustomerReturnInspection inspection = inspectionRepository
                .findByIdAndIsDeletedFalseForUpdate(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));
        DisposalRequest request = requestRepository.findDetailByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Disposal request", requestId));

        boolean damagedDisposal = request.isDamagedReturnDisposal();
        if (request.getStatus() == DisposalRequestStatus.APPROVED) {
            if (!damagedDisposal) {
                inspectionService.completeAfterDisposal(inspection.getId());
            }
            return toResponse(request);
        }
        if (request.getStatus() != DisposalRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only pending disposal requests can be approved");
        }
        if (request.getLines().isEmpty()) {
            throw new BadRequestException("Disposal request has no lines");
        }
        if (damagedDisposal) {
            // Hàng DAMAGED đã nằm trong vị trí hàng lỗi: duyệt = xuất hủy khỏi tồn kho.
            // Phiếu QC đã POSTED từ trước nên không xác nhận lại với Order-Service.
            return toResponse(approveDamagedReturnRequest(request));
        }

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(InventoryTransactionType.WRITE_OFF);
        transaction.setReferenceType(InventoryReferenceType.CUSTOMER_RETURN);
        transaction.setReferenceId(request.getId().toString());
        transaction.setBranchId(request.getBranchId());

        for (CustomerReturnInspectionLine sourceLine : request.getLines()) {
            if (sourceLine.getResult() != CustomerReturnQcResult.WRITE_OFF) {
                throw new BadRequestException("Disposal request contains a non-WRITE_OFF inspection line");
            }

            InventoryTransactionLine transactionLine = new InventoryTransactionLine();
            transactionLine.setInventoryItem(sourceLine.getInventoryItem());
            transactionLine.setLocation(null);
            transactionLine.setQuantity(sourceLine.getQuantity().negate());
            transactionLine.setBeforeQuantity(BigDecimal.ZERO);
            transactionLine.setAfterQuantity(BigDecimal.ZERO);
            transaction.addLine(transactionLine);
        }

        InventoryTransaction savedTransaction = transactionRepository.saveAndFlush(transaction);
        for (CustomerReturnInspectionLine sourceLine : request.getLines()) {
            sourceLine.setPosted(true);
            sourceLine.setTransactionId(savedTransaction.getId());
        }

        request.setStatus(DisposalRequestStatus.APPROVED);
        request.setApprovedBy(currentUserId());
        request.setApprovedAt(Instant.now());
        request.setTransactionId(savedTransaction.getId());
        DisposalRequest saved = requestRepository.saveAndFlush(request);

        inspectionService.completeAfterDisposal(inspection.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DisposalRequestResponse createDamagedReturnRequest(CreateDamagedReturnDisposalRequest command) {
        requireRequesterRole();

        CustomerReturnInspection inspection = inspectionRepository
                .findByIdAndIsDeletedFalseForUpdate(command.inspectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", command.inspectionId()));
        if (inspection.getStatus() != CustomerReturnInspectionStatus.POSTED) {
            throw new BadRequestException("Only POSTED customer return inspections can request damaged-goods disposal");
        }

        Set<UUID> requestedLineIds = command.lineIds() == null || command.lineIds().isEmpty()
                ? null
                : new HashSet<>(command.lineIds());
        if (requestedLineIds != null && requestedLineIds.size() != command.lineIds().size()) {
            throw new BadRequestException("Duplicate lineId in disposal request");
        }
        List<CustomerReturnInspectionLine> lines = inspection.getLines().stream()
                .filter(line -> requestedLineIds == null || requestedLineIds.contains(line.getId()))
                .toList();
        if (requestedLineIds != null && lines.size() != requestedLineIds.size()) {
            throw new BadRequestException("Disposal lines do not belong to inspection " + inspection.getInspectionCode());
        }
        List<CustomerReturnInspectionLine> eligibleLines = lines.stream()
                .filter(this::isDisposableDamagedLine)
                .toList();
        if (requestedLineIds != null && eligibleLines.size() != lines.size()) {
            throw new BadRequestException(
                    "Only posted DAMAGED lines without a disposal request can be disposed");
        }
        if (eligibleLines.isEmpty()) {
            throw new BadRequestException("Inspection has no DAMAGED lines awaiting disposal");
        }
        UUID branchId = disposalBranch(inspection, eligibleLines);

        DisposalRequest request = new DisposalRequest();
        request.setRequestCode(generateRequestCode());
        request.setSourceType(DisposalRequest.SOURCE_RETURN_DAMAGED);
        request.setSourceInspection(inspection);
        request.setReturnId(inspection.getReturnId());
        request.setReturnCode(inspection.getReturnCode());
        request.setBranchId(branchId);
        request.setStatus(DisposalRequestStatus.PENDING_APPROVAL);
        request.setNote(command.note() == null || command.note().isBlank() ? null : command.note().trim());

        // BaseEntity gán sẵn id nên save() sẽ merge: phải gắn các dòng (đang managed) vào
        // bản đã lưu, không phải bản transient, nếu không Hibernate báo TransientObjectException.
        DisposalRequest saved = requestRepository.saveAndFlush(request);
        eligibleLines.forEach(saved::addLine);
        requestRepository.flush();
        return toResponse(saved);
    }

    /**
     * Hàng lỗi bị hủy tại vị trí hàng lỗi, nên phiếu thuộc chi nhánh của vị trí đó (luôn có).
     * Phiếu trả cũ có thể thiếu chi nhánh nên chỉ dùng chi nhánh phiếu kiểm định làm dự phòng.
     * Một phiếu tiêu hủy chỉ thuộc một chi nhánh.
     */
    private UUID disposalBranch(CustomerReturnInspection inspection, List<CustomerReturnInspectionLine> lines) {
        Set<UUID> locationBranches = new HashSet<>();
        lines.forEach(line -> locationBranches.add(line.getDestinationLocation().getBranchId()));
        locationBranches.remove(null);
        if (locationBranches.size() > 1) {
            throw new BadRequestException("Damaged lines are in different branches; dispose them separately");
        }
        UUID branchId = locationBranches.stream().findFirst().orElse(inspection.getBranchId());
        if (branchId == null) {
            throw new BadRequestException("Cannot determine branch for damaged-goods disposal");
        }
        return branchId;
    }

    private boolean isDisposableDamagedLine(CustomerReturnInspectionLine line) {
        return !Boolean.TRUE.equals(line.getIsDeleted())
                && Boolean.TRUE.equals(line.getPosted())
                && line.getResult() == CustomerReturnQcResult.DAMAGED
                && line.getDisposalRequest() == null
                && line.getTransactionId() != null
                && line.getDestinationLocation() != null;
    }

    private DisposalRequest approveDamagedReturnRequest(DisposalRequest request) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode());
        transaction.setType(InventoryTransactionType.WRITE_OFF);
        transaction.setReferenceType(InventoryReferenceType.CUSTOMER_RETURN);
        transaction.setReferenceId(request.getId().toString());
        transaction.setBranchId(request.getBranchId());

        // Khóa balance theo thứ tự cố định để hai phiếu duyệt song song không deadlock.
        List<CustomerReturnInspectionLine> lines = request.getLines().stream()
                .sorted(Comparator
                        .comparing((CustomerReturnInspectionLine line) -> line.getInventoryItem().getId())
                        .thenComparing(line -> line.getDestinationLocation().getId()))
                .toList();
        for (CustomerReturnInspectionLine line : lines) {
            if (line.getResult() != CustomerReturnQcResult.DAMAGED || !Boolean.TRUE.equals(line.getPosted())) {
                throw new BadRequestException("Damaged disposal contains a line that is not a posted DAMAGED line");
            }
            InventoryLocation location = line.getDestinationLocation();
            if (location.getType() != InventoryLocationType.DAMAGED) {
                throw new BadRequestException("Damaged disposal line is not in a DAMAGED location");
            }
            InventoryBatch batch = receiptBatch(line);
            InventoryBalance balance = balanceRepository.findForUpdateByBatchId(batch.getId(), location.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "No damaged stock left for " + line.getInventoryItem().getReferenceCode()
                                    + " at " + location.getName()));

            BigDecimal quantity = line.getQuantity();
            BigDecimal beforeOnHand = nullSafe(balance.getQuantityOnHand());
            BigDecimal blocked = nullSafe(balance.getQuantityBlocked());
            if (beforeOnHand.compareTo(quantity) < 0 || blocked.compareTo(quantity) < 0) {
                throw new BadRequestException("Not enough damaged stock to dispose "
                        + line.getInventoryItem().getReferenceCode() + " at " + location.getName()
                        + ": required " + quantity.stripTrailingZeros().toPlainString()
                        + ", blocked " + blocked.stripTrailingZeros().toPlainString());
            }
            BigDecimal afterOnHand = beforeOnHand.subtract(quantity);
            balance.setQuantityOnHand(afterOnHand);
            balance.setQuantityBlocked(blocked.subtract(quantity));
            balanceRepository.save(balance);

            InventoryTransactionLine transactionLine = new InventoryTransactionLine();
            transactionLine.setInventoryItem(line.getInventoryItem());
            transactionLine.setLocation(location);
            transactionLine.setQuantity(quantity.negate());
            transactionLine.setBeforeQuantity(beforeOnHand);
            transactionLine.setAfterQuantity(afterOnHand);

            InventoryTransactionBatchAllocation allocation = new InventoryTransactionBatchAllocation();
            allocation.setInventoryBatch(batch);
            allocation.setQuantity(quantity.negate());
            allocation.setBeforeBatchQuantity(beforeOnHand);
            allocation.setAfterBatchQuantity(afterOnHand);
            transactionLine.addBatchAllocation(allocation);
            transaction.addLine(transactionLine);
        }

        InventoryTransaction savedTransaction = transactionRepository.saveAndFlush(transaction);
        request.setStatus(DisposalRequestStatus.APPROVED);
        request.setApprovedBy(currentUserId());
        request.setApprovedAt(Instant.now());
        request.setTransactionId(savedTransaction.getId());
        return requestRepository.saveAndFlush(request);
    }

    /** Lô mà dòng DAMAGED đã được nhập vào khi QC xác nhận (giao dịch CUSTOMER_RETURN_RECEIPT). */
    private InventoryBatch receiptBatch(CustomerReturnInspectionLine line) {
        InventoryTransaction receipt = transactionRepository.findByIdWithLines(line.getTransactionId())
                .orElseThrow(() -> new BadRequestException(
                        "Receipt transaction of damaged line is missing: " + line.getTransactionId()));
        return receipt.getLines().stream()
                .filter(receiptLine -> receiptLine.getInventoryItem() != null
                        && line.getInventoryItem().getId().equals(receiptLine.getInventoryItem().getId()))
                .flatMap(receiptLine -> receiptLine.getBatchAllocations().stream())
                .map(InventoryTransactionBatchAllocation::getInventoryBatch)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Receipt batch of damaged line is missing: " + line.getTransactionId()));
    }

    private void requireRequesterRole() {
        if (SecurityUtils.hasAnyRole(Role.QC, Role.MANAGER, Role.SUPER_ADMIN)) {
            return;
        }
        throw new AccessDeniedException("Damaged-goods disposal request requires QC or manager role");
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateRequestCode() {
        String code;
        do {
            code = "DSP-" + LocalDate.now().format(CODE_DATE)
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (requestRepository.existsByRequestCode(code));
        return code;
    }

    private void requireApprovalRole() {
        if (SecurityUtils.hasAnyRole(Role.SUPER_ADMIN, Role.MANAGER)) {
            return;
        }
        throw new AccessDeniedException("Disposal approval requires an inventory approval role");
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(SecurityUtils.currentUserId());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated user id must be a UUID");
        }
    }

    private String generateTransactionCode() {
        return "WOFF-" + LocalDate.now().format(CODE_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private DisposalRequestResponse toResponse(DisposalRequest request) {
        return new DisposalRequestResponse(
                request.getId(),
                request.getRequestCode(),
                request.getSourceType(),
                request.getSourceInspection().getId(),
                request.getSourceInspection().getInspectionCode(),
                request.getReturnId(),
                request.getReturnCode(),
                request.getBranchId(),
                request.getStatus(),
                request.getCreatedBy(),
                request.getCreatedAt(),
                request.getApprovedBy(),
                request.getApprovedAt(),
                request.getTransactionId(),
                request.getNote(),
                request.getLines().stream().map(line -> new DisposalRequestLineResponse(
                        line.getId(),
                        line.getId(),
                        line.getInventoryItem().getId(),
                        line.getInventoryItem().getReferenceCode(),
                        line.getInventoryItem().getName(),
                        line.getQuantity(),
                        // Dòng DAMAGED giữ giao dịch nhập hàng lỗi; giao dịch hủy nằm trên phiếu.
                        request.isDamagedReturnDisposal() ? request.getTransactionId() : line.getTransactionId(),
                        line.getDestinationLocation() == null ? null : line.getDestinationLocation().getId(),
                        line.getDestinationLocation() == null ? null : line.getDestinationLocation().getName()
                )).toList()
        );
    }
}
