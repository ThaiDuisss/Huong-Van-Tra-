package org.hvtra.iventoryservice.disposal.service.impl;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestLineResponse;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.disposal.service.DisposalRequestService;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisposalRequestServiceImpl implements DisposalRequestService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DisposalRequestRepository requestRepository;
    private final CustomerReturnInspectionRepository inspectionRepository;
    private final InventoryTransactionRepository transactionRepository;
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

        if (request.getStatus() == DisposalRequestStatus.APPROVED) {
            inspectionService.completeAfterDisposal(inspection.getId());
            return toResponse(request);
        }
        if (request.getStatus() != DisposalRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only pending disposal requests can be approved");
        }
        if (request.getLines().isEmpty()) {
            throw new BadRequestException("Disposal request has no lines");
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
                        line.getTransactionId()
                )).toList()
        );
    }
}
