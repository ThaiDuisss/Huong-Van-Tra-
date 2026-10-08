package org.hvtra.iventoryservice.customerreturn.service.impl;

import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcDispositionReportRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcSummaryBatchRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.PostCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionLineResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryLineResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnActualCondition;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnItemResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.outbox.publisher.InventoryOutboxPublisher;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerReturnInspectionServiceImpl implements CustomerReturnInspectionService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DEFAULT_BATCH_PREFIX = "RETURN-DEFAULT-";

    private final CustomerReturnInspectionRepository inspectionRepository;
    private final OrderReturnClient orderReturnClient;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final InventoryInboxRepository inboxRepository;
    private final InventoryOutboxPublisher inventoryOutboxPublisher;

    @Value("${inventory.order.events.return-disposition.group-id:inventory-return-disposition}")
    private String consumerGroup;

    @Override
    @Transactional
    public CustomerReturnInspectionResponse createInspection(
            UUID returnId,
            CreateCustomerReturnInspectionRequest request) {
        requireQcRole();
        return createInspection(returnId, null, request);
    }

    @Override
    @Transactional
    public CustomerReturnInspectionResponse createInspection(
            UUID returnId,
            UUID commandId,
            CreateCustomerReturnInspectionRequest request) {
        OrderReturnResponse orderReturn = verifiedReceivedReturn(returnId);
        Map<UUID, OrderReturnItemResponse> returnItems = returnItemsById(orderReturn);
        Map<UUID, BigDecimal> allocatedQuantities = allocatedQuantities(returnId);
        Set<UUID> requestedReturnItems = new HashSet<>();

        CustomerReturnInspection inspection = new CustomerReturnInspection();
        inspection.setInspectionCode(generateInspectionCode());
        inspection.setReturnId(orderReturn.id());
        inspection.setReturnCode(orderReturn.returnCode());
        inspection.setCommandId(commandId);
        inspection.setBranchId(orderReturn.branchId());
        inspection.setOrderId(orderReturn.orderId());
        inspection.setOrderCode(orderReturn.orderCode());
        inspection.setStatus(CustomerReturnInspectionStatus.CREATED);
        inspection.setNote(request.note());

        for (CreateCustomerReturnInspectionLineRequest lineRequest : request.lines()) {
            if (!requestedReturnItems.add(lineRequest.returnItemId())) {
                throw new BadRequestException("Duplicate returnItemId in inspection: " + lineRequest.returnItemId());
            }

            OrderReturnItemResponse returnItem = returnItems.get(lineRequest.returnItemId());
            if (returnItem == null) {
                throw new BadRequestException("Return item does not belong to return: " + lineRequest.returnItemId());
            }
            validatePositive(lineRequest.quantity(), "quantity");
            validateWithinReturnedQuantity(returnItem, allocatedQuantities.get(returnItem.id()), lineRequest.quantity());
            validateConditionResult(lineRequest.actualCondition(), lineRequest.result(), commandId != null, false);

            InventoryItem item = activeProductVariantItem(returnItem.productVariantId());
            InventoryLocation destination = resolveDestination(
                    lineRequest.result(),
                    lineRequest.destinationLocationId());

            CustomerReturnInspectionLine line = new CustomerReturnInspectionLine();
            line.setReturnItemId(returnItem.id());
            line.setOrderItemId(returnItem.orderItemId());
            line.setProductVariantId(returnItem.productVariantId());
            line.setInventoryItem(item);
            line.setQuantity(lineRequest.quantity());
            line.setActualCondition(lineRequest.actualCondition());
            line.setResult(lineRequest.result());
            line.setDestinationLocation(destination);
            line.setNote(lineRequest.note());
            inspection.addLine(line);
        }

        CustomerReturnInspection saved = inspectionRepository.saveAndFlush(inspection);
        return toEnrichedResponse(saved, orderReturn);
    }

    @Override
    @Transactional
    public CustomerReturnInspectionResponse postInspection(UUID inspectionId) {
        return postInspection(inspectionId, null);
    }

    @Override
    @Transactional
    public CustomerReturnInspectionResponse postInspection(UUID inspectionId, PostCustomerReturnInspectionRequest request) {
        requireQcRole();
        CustomerReturnInspection inspectionSnapshot = inspectionRepository
                .findByIdAndIsDeletedFalse(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));

        // Idempotency check 1: before pessimistic lock
        if (inspectionSnapshot.getStatus() == CustomerReturnInspectionStatus.POSTED) {
            log.info("Customer return inspection is already POSTED. Returning existing inspection without reprocessing: inspectionId={}", inspectionId);
            acknowledgePostedInspection(inspectionSnapshot);
            return getInspection(inspectionId);
        }
        if (inspectionSnapshot.getStatus() == CustomerReturnInspectionStatus.CANCELLED) {
            throw new BadRequestException("Cancelled customer return inspection cannot be posted: " + inspectionId);
        }

        OrderReturnResponse orderReturn = verifiedReceivedReturn(inspectionSnapshot.getReturnId());
        Map<UUID, OrderReturnItemResponse> returnItems = returnItemsById(orderReturn);

        List<CustomerReturnInspection> lockedInspections = inspectionRepository
                .findByReturnIdForUpdate(inspectionSnapshot.getReturnId());
        CustomerReturnInspection inspection = lockedInspections.stream()
                .filter(candidate -> candidate.getId().equals(inspectionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));

        // Idempotency check 2: under pessimistic lock
        if (inspection.getStatus() == CustomerReturnInspectionStatus.POSTED) {
            log.info("Customer return inspection is already POSTED (under lock). Returning existing inspection without reprocessing: inspectionId={}", inspectionId);
            acknowledgePostedInspection(inspection);
            return toEnrichedResponse(inspection, orderReturn);
        }
        if (inspection.getStatus() == CustomerReturnInspectionStatus.CANCELLED) {
            throw new BadRequestException("Cancelled customer return inspection cannot be posted: " + inspectionId);
        }

        applyQcOverrides(inspection, request);

        // Validate all observations before issuing any Inventory movement.
        for (CustomerReturnInspectionLine line : inspection.getLines()) {
            if (!Boolean.TRUE.equals(line.getPosted())) {
                validateConditionResult(line.getActualCondition(), line.getResult(), false, true);
                if (line.getResult() != CustomerReturnQcResult.WRITE_OFF) {
                    validateDestinationForPosting(line.getResult(), line.getDestinationLocation());
                }
            }
        }

        Map<UUID, BigDecimal> postedQuantities = postedQuantities(lockedInspections);
        List<CustomerReturnInspectionLine> writeOffLines = new ArrayList<>();
        for (CustomerReturnInspectionLine line : inspection.getLines()) {
            if (Boolean.TRUE.equals(line.getPosted())) {
                continue;
            }
            OrderReturnItemResponse returnItem = returnItems.get(line.getReturnItemId());
            if (returnItem == null) {
                throw new BadRequestException("Return item no longer exists in Order-Service: " + line.getReturnItemId());
            }
            validatePositive(line.getQuantity(), "quantity");
            validateWithinReturnedQuantity(returnItem, postedQuantities.get(returnItem.id()), line.getQuantity());
            if (line.getResult() == CustomerReturnQcResult.WRITE_OFF) {
                InventoryItem item = line.getInventoryItem();
                if (item == null || Boolean.TRUE.equals(item.getIsDeleted())
                        || item.getStatus() != InventoryStatus.ACTIVE
                        || item.getItemType() != InventoryItemType.PRODUCT_VARIANT
                        || !Objects.equals(item.getReferenceId(), returnItem.productVariantId())) {
                    throw new BadRequestException("WRITE_OFF requires an active mapped inventory item");
                }
                writeOffLines.add(line);
                postedQuantities.merge(line.getReturnItemId(), line.getQuantity(), BigDecimal::add);
                continue;
            }
            postLine(inspection, line);
            line.setPosted(true);
            postedQuantities.merge(line.getReturnItemId(), line.getQuantity(), BigDecimal::add);
        }

        postWriteOffLines(inspection, writeOffLines);
        boolean allLinesPosted = inspection.getLines().stream()
                .allMatch(line -> Boolean.TRUE.equals(line.getPosted()));
        inspection.setStatus(allLinesPosted
                ? CustomerReturnInspectionStatus.POSTED
                : CustomerReturnInspectionStatus.PROCESSING);
        inspection.setPostedAt(allLinesPosted ? Instant.now() : null);
        CustomerReturnInspection saved = inspectionRepository.saveAndFlush(inspection);

        if (allLinesPosted) {
            acknowledgePostedInspection(saved);
        }

        return toEnrichedResponse(saved, orderReturn);
    }

    @Override
    @Transactional
    public void completeAfterDisposal(UUID inspectionId) {
        CustomerReturnInspection inspection = inspectionRepository
                .findByIdAndIsDeletedFalseForUpdate(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));
        if (inspection.getStatus() == CustomerReturnInspectionStatus.CANCELLED) {
            throw new BadRequestException("Cancelled customer return inspection cannot be completed: " + inspectionId);
        }
        if (!inspection.getLines().stream().allMatch(line -> Boolean.TRUE.equals(line.getPosted()))) {
            inspection.setStatus(CustomerReturnInspectionStatus.PROCESSING);
            inspectionRepository.saveAndFlush(inspection);
            return;
        }
        if (inspection.getStatus() != CustomerReturnInspectionStatus.POSTED) {
            inspection.setStatus(CustomerReturnInspectionStatus.POSTED);
            inspection.setPostedAt(Instant.now());
            inspectionRepository.saveAndFlush(inspection);
        }
        acknowledgePostedInspection(inspection);
    }

    private void postWriteOffLines(
            CustomerReturnInspection inspection,
            List<CustomerReturnInspectionLine> writeOffLines) {
        if (writeOffLines.isEmpty()) {
            return;
        }

        // The inspection lock is acquired before the disposal lock, matching legacy approval.
        DisposalRequest legacyRequest = disposalRequestRepository.findBySourceInspectionId(inspection.getId())
                .map(request -> disposalRequestRepository.findDetailByIdForUpdate(request.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Disposal request", request.getId())))
                .orElse(null);
        if (legacyRequest != null && (!"CUSTOMER_RETURN_INSPECTION".equals(legacyRequest.getSourceType())
                || legacyRequest.getStatus() != DisposalRequestStatus.PENDING_APPROVAL)) {
            throw new BadRequestException("Disposal record is inconsistent with unposted return lines; reconciliation is required");
        }

        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.WRITE_OFF, inspection.getBranchId(), inspection.getReturnId());
        if (legacyRequest != null) {
            transaction.setReferenceId(legacyRequest.getId().toString());
        }
        for (CustomerReturnInspectionLine line : writeOffLines) {
            transaction.addLine(createTransactionLine(
                    line.getInventoryItem(), null, line.getQuantity().negate(), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        InventoryTransaction saved = transactionRepository.saveAndFlush(transaction);
        writeOffLines.forEach(line -> {
            line.setPosted(true);
            line.setTransactionId(saved.getId());
        });

        if (legacyRequest != null) {
            // Close the old audit record only on explicit QC posting. No bulk history rewrite.
            legacyRequest.setStatus(DisposalRequestStatus.APPROVED);
            legacyRequest.setApprovedBy(UUID.fromString(SecurityUtils.currentUserId()));
            legacyRequest.setApprovedAt(Instant.now());
            legacyRequest.setTransactionId(saved.getId());
            disposalRequestRepository.saveAndFlush(legacyRequest);
        }
    }

    private void requireQcRole() {
        Set<String> allowed = Set.of("QC", "QC_STAFF", "MANAGER", "ADMIN", "SUPER_ADMIN",
                "BRANCH_MANAGER", "INVENTORY_MANAGER");
        boolean authorized = SecurityUtils.currentRoles().stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""))
                .anyMatch(allowed::contains);
        if (!authorized) {
            throw new AccessDeniedException("Customer return disposition requires QC or manager role");
        }
    }

    private void acknowledgePostedInspection(CustomerReturnInspection inspection) {
        // A POSTED inspection is durable evidence that stock was already handled. Keep
        // acknowledgement repair idempotent so a retry never posts balances twice.
        if (inspection.getCommandId() == null) {
            log.debug("Standalone inspection has no source command; skip disposition acknowledgement. inspectionId={}",
                    inspection.getId());
            return;
        }
        Optional<InventoryInbox> inboxOpt = inboxRepository.findByCommandIdAndConsumerGroup(
                inspection.getCommandId().toString(),
                consumerGroup
        );
        if (inboxOpt.isPresent()) {
            InventoryInbox inbox = inboxOpt.get();
            if (inbox.getInspectionId() != null
                    && !inspection.getId().toString().equals(inbox.getInspectionId())) {
                throw new IllegalStateException("Source command is linked to a different inspection");
            }
            if (inbox.getStatus() != InboxStatus.PROCESSED) {
                inbox.setStatus(InboxStatus.PROCESSED);
                inbox.setProcessedAt(Instant.now());
                inbox.setInspectionId(inspection.getId().toString());
                inbox.setLastError(null);
                inboxRepository.saveAndFlush(inbox);
            }

            inventoryOutboxPublisher.publishReturnInventoryDispositionProcessed(
                    inbox.getCommandId(),
                    inspection.getReturnId().toString(),
                    inspection.getId().toString(),
                    inbox.getSagaId(),
                    inbox.getCorrelationId()
            );
            log.info("Ensured processed outbox event for posted inspection. inspectionId={}, commandId={}, returnId={}",
                    inspection.getId(), inbox.getCommandId(), inspection.getReturnId());
        } else {
            log.warn("No inbox record found for posted inspectionId={}, commandId={}. Cannot publish disposition acknowledgement.",
                    inspection.getId(), inspection.getCommandId());
        }
    }

    @Override
    public CustomerReturnInspectionResponse getInspection(UUID inspectionId) {
        CustomerReturnInspection inspection = inspectionRepository.findByIdAndIsDeletedFalse(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));
        OrderReturnResponse orderReturn = null;
        try {
            orderReturn = orderReturnClient.getReturnById(inspection.getReturnId());
        } catch (Exception ex) {
            log.warn("Could not fetch OrderReturnResponse for returnId={}: {}", inspection.getReturnId(), ex.getMessage());
        }
        return toEnrichedResponse(inspection, orderReturn);
    }

    @Override
    public Page<CustomerReturnInspectionResponse> listInspections(
            String returnCode,
            String orderCode,
            CustomerReturnInspectionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        Instant fromCreatedAt = fromDate == null ? null : fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toCreatedBefore = toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Specification<CustomerReturnInspection> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("isDeleted")));
            if (returnCode != null && !returnCode.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("returnCode")),
                        "%" + returnCode.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (orderCode != null && !orderCode.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("orderCode")),
                        "%" + orderCode.trim().toLowerCase(Locale.ROOT) + "%"));
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

        Page<CustomerReturnInspection> inspectionPage = inspectionRepository.findAll(specification, pageable);
        if (inspectionPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> inspectionIds = inspectionPage.getContent().stream()
                .map(CustomerReturnInspection::getId)
                .toList();
        Map<UUID, CustomerReturnInspection> inspectionsById = inspectionRepository
                .findAllByIdInWithLines(inspectionIds)
                .stream()
                .collect(Collectors.toMap(CustomerReturnInspection::getId, Function.identity()));
        List<CustomerReturnInspectionResponse> content = inspectionIds.stream()
                .map(inspectionsById::get)
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, pageable, inspectionPage.getTotalElements());
    }

    @Override
    public CustomerReturnQcSummaryResponse getQcSummary(UUID returnId) {
        OrderReturnResponse orderReturn = fetchReturn(returnId);
        QcProgress qcProgress = buildQcProgress(
                inspectionRepository.findQcProgressByReturnId(returnId)
        );

        List<CustomerReturnQcSummaryLineResponse> lines = orderReturn.items().stream()
                .map(item -> {
                    BigDecimal posted = qcProgress.postedQuantity(item.id());
                    BigDecimal remaining = item.quantity().subtract(posted);
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                        remaining = BigDecimal.ZERO;
                    }
                    return new CustomerReturnQcSummaryLineResponse(
                            item.id(),
                            item.orderItemId(),
                            item.productVariantId(),
                            item.quantity(),
                            posted,
                            remaining,
                            lineQcStatus(item.quantity(), posted),
                            item.condition(),
                            item.reason(),
                            qcProgress.dispositions(item.id()));
                })
                .toList();

        return new CustomerReturnQcSummaryResponse(
                orderReturn.id(),
                orderReturn.returnCode(),
                orderReturn.status(),
                orderReturn.branchId(),
                orderReturn.orderId(),
                orderReturn.orderCode(),
                qcProgress.inspectionId(),
                qcProgress.inspectionStatus(),
                overallQcStatus(lines),
                lines,
                qcProgress.inspectedAt());
    }

    @Override
    public List<CustomerReturnQcSummaryResponse> getQcSummaries(CustomerReturnQcSummaryBatchRequest request) {
        if (request == null || request.returns() == null || request.returns().isEmpty()) {
            return List.of();
        }

        List<UUID> returnIds = request.returns().stream()
                .map(CustomerReturnQcSummaryBatchRequest.CustomerReturnSnapshot::returnId)
                .distinct()
                .toList();
        Map<UUID, List<CustomerReturnInspection>> inspectionsByReturnId = inspectionRepository
                .findQcProgressByReturnIds(returnIds)
                .stream()
                .collect(Collectors.groupingBy(CustomerReturnInspection::getReturnId));

        return request.returns().stream()
                .map(returnSnapshot -> toQcSummary(
                        returnSnapshot,
                        buildQcProgress(inspectionsByReturnId.getOrDefault(returnSnapshot.returnId(), List.of()))
                ))
                .toList();
    }

    @Override
    public List<CustomerReturnQcDispositionReportResponse> getQcDispositionReport(
            CustomerReturnQcDispositionReportRequest request
    ) {
        if (request == null || request.returnIds() == null || request.returnIds().isEmpty()) {
            return List.of();
        }

        List<UUID> returnIds = request.returnIds().stream()
                .distinct()
                .toList();

        return inspectionRepository.sumDispositionByReturnIds(returnIds);
    }

    private void postLine(CustomerReturnInspection inspection, CustomerReturnInspectionLine line) {
        InventoryLocation location = validateDestinationForPosting(
                line.getResult(),
                line.getDestinationLocation());
        InventoryBatch batch = defaultBatch(line.getInventoryItem());
        InventoryBalance balance = balanceRepository
                .findForUpdateByBatchId(batch.getId(), location.getId())
                .orElseGet(() -> newBalance(line.getInventoryItem(), batch, location));

        BigDecimal beforeOnHand = nullSafe(balance.getQuantityOnHand());
        BigDecimal afterOnHand = beforeOnHand.add(line.getQuantity());
        balance.setQuantityOnHand(afterOnHand);

        // QUARANTINE is legacy-only: existing inspections can finish without rewriting their disposition.
        if (line.getResult() == CustomerReturnQcResult.QUARANTINE || line.getResult() == CustomerReturnQcResult.DAMAGED) {
            balance.setQuantityBlocked(nullSafe(balance.getQuantityBlocked()).add(line.getQuantity()));
        }
        balanceRepository.save(balance);

        InventoryTransaction transaction = createTransaction(
                InventoryTransactionType.CUSTOMER_RETURN_RECEIPT,
                inspection.getBranchId(),
                inspection.getReturnId());
        InventoryTransactionLine transLine = createTransactionLine(
                line.getInventoryItem(),
                location,
                line.getQuantity(),
                beforeOnHand,
                afterOnHand);

        InventoryTransactionBatchAllocation transAlloc = new InventoryTransactionBatchAllocation();
        transAlloc.setInventoryBatch(batch);
        transAlloc.setQuantity(line.getQuantity());
        transAlloc.setBeforeBatchQuantity(beforeOnHand);
        transAlloc.setAfterBatchQuantity(afterOnHand);
        transLine.addBatchAllocation(transAlloc);

        transaction.addLine(transLine);
        line.setTransactionId(transactionRepository.saveAndFlush(transaction).getId());
    }

    private InventoryTransaction createTransaction(
            InventoryTransactionType type,
            UUID branchId,
            UUID returnId) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionCode(generateTransactionCode(type));
        transaction.setType(type);
        transaction.setReferenceType(InventoryReferenceType.CUSTOMER_RETURN);
        transaction.setReferenceId(returnId.toString());
        transaction.setBranchId(branchId);
        return transaction;
    }

    private InventoryTransactionLine createTransactionLine(
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

    private InventoryBatch defaultBatch(InventoryItem item) {
        return batchRepository.findFirstByInventoryItemId(item.getId())
                .orElseGet(() -> {
                    InventoryBatch batch = new InventoryBatch();
                    batch.setInventoryItem(item);
                    batch.setBatchCode(DEFAULT_BATCH_PREFIX + item.getId().toString().substring(0, 8).toUpperCase());
                    batch.setStatus(InventoryBatchStatus.AVAILABLE);
                    batch.setSourceType(InventoryReferenceType.CUSTOMER_RETURN);
                    return batchRepository.saveAndFlush(batch);
                });
    }

    private InventoryBalance newBalance(
            InventoryItem item,
            InventoryBatch batch,
            InventoryLocation location) {
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(BigDecimal.ZERO);
        balance.setQuantityReserved(BigDecimal.ZERO);
        balance.setQuantityBlocked(BigDecimal.ZERO);
        return balance;
    }

    private OrderReturnResponse verifiedReceivedReturn(UUID returnId) {
        OrderReturnResponse orderReturn = fetchReturn(returnId);
        if (orderReturn == null) {
            throw new ResourceNotFoundException("Order return", returnId);
        }
        if (orderReturn.status() != OrderReturnStatus.RECEIVED) {
            throw new BadRequestException("Only RECEIVED order returns can be QC'd");
        }
        if (orderReturn.items() == null || orderReturn.items().isEmpty()) {
            throw new BadRequestException("Order return has no items");
        }
        return orderReturn;
    }

    private OrderReturnResponse fetchReturn(UUID returnId) {
        try {
            return orderReturnClient.getReturnById(returnId);
        } catch (FeignException.NotFound exception) {
            throw new ResourceNotFoundException("Order return", returnId);
        }
    }

    private Map<UUID, OrderReturnItemResponse> returnItemsById(OrderReturnResponse orderReturn) {
        return orderReturn.items().stream()
                .collect(Collectors.toMap(OrderReturnItemResponse::id, Function.identity()));
    }

    private Map<UUID, BigDecimal> postedQuantities(UUID returnId) {
        return postedQuantities(inspectionRepository.findQcProgressByReturnId(returnId));
    }

    private Map<UUID, BigDecimal> allocatedQuantities(UUID returnId) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        inspectionRepository.findQcProgressByReturnId(returnId).stream()
                .filter(inspection -> inspection.getStatus() != CustomerReturnInspectionStatus.CANCELLED)
                .flatMap(inspection -> inspection.getLines().stream())
                .filter(line -> !Boolean.TRUE.equals(line.getIsDeleted()))
                .forEach(line -> totals.merge(
                        line.getReturnItemId(),
                        line.getQuantity(),
                        BigDecimal::add
                ));
        return totals;
    }

    private Map<UUID, BigDecimal> postedQuantities(List<CustomerReturnInspection> inspections) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        inspections.stream()
                .flatMap(inspection -> inspection.getLines().stream())
                .filter(line -> Boolean.TRUE.equals(line.getPosted()))
                .forEach(line -> totals.merge(
                        line.getReturnItemId(),
                        line.getQuantity(),
                        BigDecimal::add
                ));
        return totals;
    }

    private InventoryItem activeProductVariantItem(UUID productVariantId) {
        if (productVariantId == null) {
            throw new BadRequestException("Order return item productVariantId is required");
        }
        InventoryItem item = itemRepository
                .findByReferenceIdAndItemType(productVariantId, InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory item for product variant",
                        productVariantId));
        if (item.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory item is inactive: " + item.getId());
        }
        return item;
    }

    private InventoryLocation resolveDestination(
            CustomerReturnQcResult result,
            UUID destinationLocationId) {
        if (result == CustomerReturnQcResult.WRITE_OFF) {
            return null;
        }
        if (destinationLocationId == null) {
            throw new BadRequestException("destinationLocationId is required for " + result);
        }
        InventoryLocation location = locationRepository.findById(destinationLocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory location", destinationLocationId));
        return validateDestinationForPosting(result, location);
    }

    private InventoryLocation validateDestinationForPosting(
            CustomerReturnQcResult result,
            InventoryLocation location) {
        if (location == null) {
            throw new BadRequestException("destinationLocationId is required for " + result);
        }
        if (location.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory location is inactive: " + location.getId());
        }
        if (result == CustomerReturnQcResult.RESTOCK && !InventoryLocationSaleability.isSaleable(location)) {
            throw new BadRequestException("RESTOCK destination must be an active saleable location");
        }
        // Preserve destination validation when posting a stored legacy inspection.
        if (result == CustomerReturnQcResult.QUARANTINE && location.getType() != InventoryLocationType.QUARANTINE) {
            throw new BadRequestException("QUARANTINE destination must be a QUARANTINE location");
        }
        if (result == CustomerReturnQcResult.DAMAGED && location.getType() != InventoryLocationType.DAMAGED) {
            throw new BadRequestException("DAMAGED destination must be a DAMAGED location");
        }
        return location;
    }

    private void validateConditionResult(
            CustomerReturnActualCondition actualCondition,
            CustomerReturnQcResult result,
            boolean allowDraft,
            boolean allowLegacyResult) {
        if (result == null) {
            throw new BadRequestException("Customer return disposition result is required");
        }
        if (!result.isAllowedForNewReturn() && !allowLegacyResult) {
            throw new BadRequestException("QUARANTINE is no longer supported for new customer returns; use DAMAGED");
        }
        if (actualCondition == null && allowDraft) {
            return;
        }
        if (actualCondition == null) {
            throw new BadRequestException("QC actual condition is required before confirming inspection");
        }
        if (result == CustomerReturnQcResult.RESTOCK && actualCondition != CustomerReturnActualCondition.SEALED) {
            throw new BadRequestException("RESTOCK is not allowed for QC actual condition " + actualCondition);
        }
    }

    private void applyQcOverrides(CustomerReturnInspection inspection, PostCustomerReturnInspectionRequest request) {
        if (request == null || request.lines() == null) {
            return;
        }
        Set<UUID> updatedItems = new HashSet<>();
        for (PostCustomerReturnInspectionRequest.LineOverride override : request.lines()) {
            if (override == null || (override.lineId() == null && override.returnItemId() == null)) {
                throw new BadRequestException("QC override must identify an inspection line");
            }
            CustomerReturnInspectionLine line = inspection.getLines().stream()
                    .filter(candidate -> (override.lineId() == null || override.lineId().equals(candidate.getId()))
                            && (override.returnItemId() == null || override.returnItemId().equals(candidate.getReturnItemId())))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("QC override does not belong to inspection"));
            if (!updatedItems.add(line.getReturnItemId())) {
                throw new BadRequestException("Duplicate QC override for inspection line");
            }
            if (Boolean.TRUE.equals(line.getPosted())) {
                throw new BadRequestException("Posted inspection lines cannot be changed");
            }
            if (override.result() != null) {
                if (!override.result().isAllowedForNewReturn() && override.result() != line.getResult()) {
                    throw new BadRequestException("QUARANTINE is no longer supported for new customer returns; use DAMAGED");
                }
                line.setResult(override.result());
            }
            if (override.actualCondition() != null) {
                line.setActualCondition(override.actualCondition());
            }
            if (override.note() != null) {
                line.setNote(override.note().isBlank() ? null : override.note().trim());
            }
            validateConditionResult(line.getActualCondition(), line.getResult(), false, true);
            if (line.getResult() == CustomerReturnQcResult.WRITE_OFF) {
                line.setDestinationLocation(null);
            } else if (override.destinationLocationId() != null) {
                line.setDestinationLocation(resolveDestination(line.getResult(), override.destinationLocationId()));
            }
        }
    }

    private boolean hasReasonableNote(String lineNote, String inspectionNote) {
        return hasTextAtLeast(lineNote, 5) || hasTextAtLeast(inspectionNote, 5);
    }

    private boolean hasTextAtLeast(String text, int length) {
        return text != null && text.trim().length() >= length;
    }

    private void validateWithinReturnedQuantity(
            OrderReturnItemResponse returnItem,
            BigDecimal postedQuantity,
            BigDecimal requestedQuantity) {
        BigDecimal posted = nullSafe(postedQuantity);
        BigDecimal total = posted.add(requestedQuantity);
        if (total.compareTo(returnItem.quantity()) > 0) {
            throw new BadRequestException(
                    "QC quantity exceeds returned quantity for returnItemId " + returnItem.id());
        }
    }

    private void validatePositive(BigDecimal quantity, String field) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(field + " must be greater than 0");
        }
    }

    private CustomerReturnQcSummaryResponse toQcSummary(
            CustomerReturnQcSummaryBatchRequest.CustomerReturnSnapshot returnSnapshot,
            QcProgress qcProgress
    ) {
        List<CustomerReturnQcSummaryLineResponse> lines = returnSnapshot.lines().stream()
                .map(item -> {
                    BigDecimal posted = qcProgress.postedQuantity(item.returnItemId());
                    BigDecimal remaining = item.quantity().subtract(posted);
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                        remaining = BigDecimal.ZERO;
                    }
                    return new CustomerReturnQcSummaryLineResponse(
                            item.returnItemId(),
                            item.orderItemId(),
                            item.productVariantId(),
                            item.quantity(),
                            posted,
                            remaining,
                            lineQcStatus(item.quantity(), posted),
                            item.condition(),
                            item.reason(),
                            qcProgress.dispositions(item.returnItemId()));
                })
                .toList();

        return new CustomerReturnQcSummaryResponse(
                returnSnapshot.returnId(),
                returnSnapshot.returnCode(),
                returnSnapshot.returnStatus(),
                returnSnapshot.branchId(),
                returnSnapshot.orderId(),
                returnSnapshot.orderCode(),
                qcProgress.inspectionId(),
                qcProgress.inspectionStatus(),
                overallQcStatus(lines),
                lines,
                qcProgress.inspectedAt());
    }

    private record DispositionKey(
            CustomerReturnQcResult result,
            UUID destinationLocationId,
            String destinationLocationName,
            UUID transactionId,
            InventoryTransactionType transactionType,
            Instant processedAt
    ) {}

    private QcProgress buildQcProgress(List<CustomerReturnInspection> inspections) {
        CustomerReturnInspection latestInspection = inspections.isEmpty() ? null : inspections.getFirst();
        Map<UUID, BigDecimal> postedQuantities = new HashMap<>();
        Map<UUID, Map<DispositionKey, BigDecimal>> dispositionsByItem = new HashMap<>();

        inspections.forEach(inspection -> inspection.getLines().stream()
                .filter(line -> Boolean.TRUE.equals(line.getPosted()))
                .forEach(line -> {
                    postedQuantities.merge(line.getReturnItemId(), line.getQuantity(), BigDecimal::add);
                    UUID locId = line.getDestinationLocation() != null ? line.getDestinationLocation().getId() : null;
                    String locName = line.getDestinationLocation() != null ? line.getDestinationLocation().getName() : null;
                    InventoryTransactionType transactionType = line.getResult() == CustomerReturnQcResult.WRITE_OFF
                            ? InventoryTransactionType.WRITE_OFF
                            : InventoryTransactionType.CUSTOMER_RETURN_RECEIPT;
                    DispositionKey key = new DispositionKey(
                            line.getResult(),
                            locId,
                            locName,
                            line.getTransactionId(),
                            transactionType,
                            inspection.getPostedAt()
                    );
                    dispositionsByItem
                            .computeIfAbsent(line.getReturnItemId(), ignored -> new LinkedHashMap<>())
                            .merge(key, line.getQuantity(), BigDecimal::add);
                }));

        Instant inspectedAt = inspections.stream()
                .map(CustomerReturnInspection::getPostedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(latestInspection != null ? latestInspection.getPostedAt() : null);

        return new QcProgress(
                latestInspection == null ? null : latestInspection.getId(),
                latestInspection == null ? null : latestInspection.getStatus(),
                postedQuantities,
                dispositionsByItem,
                inspectedAt
        );
    }

    private record QcProgress(
            UUID inspectionId,
            CustomerReturnInspectionStatus inspectionStatus,
            Map<UUID, BigDecimal> postedQuantities,
            Map<UUID, Map<DispositionKey, BigDecimal>> dispositionsByItem,
            Instant inspectedAt
    ) {
        private BigDecimal postedQuantity(UUID returnItemId) {
            return postedQuantities.getOrDefault(returnItemId, BigDecimal.ZERO);
        }

        private List<CustomerReturnQcDispositionSummaryResponse> dispositions(UUID returnItemId) {
            return dispositionsByItem
                    .getOrDefault(returnItemId, Map.of())
                    .entrySet()
                    .stream()
                    .map(entry -> new CustomerReturnQcDispositionSummaryResponse(
                            entry.getKey().result(),
                            entry.getValue(),
                            entry.getKey().destinationLocationId(),
                            entry.getKey().destinationLocationName(),
                            entry.getKey().transactionId(),
                            entry.getKey().transactionType(),
                            "PROCESSED",
                            entry.getKey().processedAt()
                    ))
                    .toList();
        }
    }

    private CustomerReturnInspectionResponse toEnrichedResponse(
            CustomerReturnInspection inspection,
            OrderReturnResponse orderReturn
    ) {
        String customerName = orderReturn == null ? null : orderReturn.customerName();
        Map<UUID, OrderReturnItemResponse> itemsByReturnItemId = orderReturn == null
                ? Map.of()
                : returnItemsById(orderReturn);

        List<CustomerReturnInspectionLineResponse> lines = inspection.getLines().stream()
                .map(line -> {
                    OrderReturnItemResponse returnItem = itemsByReturnItemId.get(line.getReturnItemId());
                    String condition = returnItem == null || returnItem.condition() == null
                            ? null
                            : returnItem.condition().name();
                    String returnReason = returnItem == null ? null : returnItem.reason();
                    InventoryLocation destination = line.getDestinationLocation();
                    DisposalRequest disposalRequest = line.getDisposalRequest();
                    return new CustomerReturnInspectionLineResponse(
                            line.getId(),
                            line.getReturnItemId(),
                            line.getOrderItemId(),
                            line.getProductVariantId(),
                            line.getInventoryItem().getId(),
                            line.getInventoryItem().getReferenceCode(),
                            line.getInventoryItem().getName(),
                            line.getQuantity(),
                            line.getResult(),
                            destination == null ? null : destination.getId(),
                            destination == null ? null : destination.getName(),
                            line.getNote(),
                            line.getPosted(),
                            line.getTransactionId(),
                            disposalRequest == null ? null : disposalRequest.getId(),
                            disposalRequest == null ? null : disposalRequest.getRequestCode(),
                            disposalRequest == null ? null : disposalRequest.getStatus(),
                            condition,
                            returnReason,
                            line.getActualCondition()
                    );
                })
                .toList();

        return new CustomerReturnInspectionResponse(
                inspection.getId(),
                inspection.getInspectionCode(),
                inspection.getReturnId(),
                inspection.getReturnCode(),
                inspection.getBranchId(),
                inspection.getOrderId(),
                inspection.getOrderCode(),
                customerName,
                inspection.getStatus(),
                inspection.getCreatedAt(),
                inspection.getPostedAt(),
                inspection.getNote(),
                lines
        );
    }

    private CustomerReturnInspectionResponse toResponse(CustomerReturnInspection inspection) {
        return new CustomerReturnInspectionResponse(
                inspection.getId(),
                inspection.getInspectionCode(),
                inspection.getReturnId(),
                inspection.getReturnCode(),
                inspection.getBranchId(),
                inspection.getOrderId(),
                inspection.getOrderCode(),
                inspection.getStatus(),
                inspection.getCreatedAt(),
                inspection.getPostedAt(),
                inspection.getNote(),
                inspection.getLines().stream().map(this::toLineResponse).toList());
    }

    private CustomerReturnInspectionLineResponse toLineResponse(CustomerReturnInspectionLine line) {
        InventoryLocation destination = line.getDestinationLocation();
        DisposalRequest disposalRequest = line.getDisposalRequest();
        return new CustomerReturnInspectionLineResponse(
                line.getId(),
                line.getReturnItemId(),
                line.getOrderItemId(),
                line.getProductVariantId(),
                line.getInventoryItem().getId(),
                line.getInventoryItem().getReferenceCode(),
                line.getInventoryItem().getName(),
                line.getQuantity(),
                line.getResult(),
                destination == null ? null : destination.getId(),
                destination == null ? null : destination.getName(),
                line.getNote(),
                line.getPosted(),
                line.getTransactionId(),
                disposalRequest == null ? null : disposalRequest.getId(),
                disposalRequest == null ? null : disposalRequest.getRequestCode(),
                disposalRequest == null ? null : disposalRequest.getStatus(),
                null,
                null,
                line.getActualCondition());
    }

    private CustomerReturnQcStatus lineQcStatus(BigDecimal returnedQuantity, BigDecimal postedQuantity) {
        if (postedQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return CustomerReturnQcStatus.PENDING;
        }
        if (postedQuantity.compareTo(returnedQuantity) >= 0) {
            return CustomerReturnQcStatus.COMPLETED;
        }
        return CustomerReturnQcStatus.PARTIAL;
    }

    private CustomerReturnQcStatus overallQcStatus(List<CustomerReturnQcSummaryLineResponse> lines) {
        boolean anyPosted = lines.stream()
                .anyMatch(line -> line.postedQcQuantity().compareTo(BigDecimal.ZERO) > 0);
        boolean allCompleted = lines.stream()
                .allMatch(line -> line.status() == CustomerReturnQcStatus.COMPLETED);
        if (allCompleted) {
            return CustomerReturnQcStatus.COMPLETED;
        }
        return anyPosted ? CustomerReturnQcStatus.PARTIAL : CustomerReturnQcStatus.PENDING;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateInspectionCode() {
        String code;
        do {
            code = "CR-QC-" + LocalDate.now().format(CODE_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (inspectionRepository.existsByInspectionCode(code));
        return code;
    }

    private String generateTransactionCode(InventoryTransactionType type) {
        String prefix = type == InventoryTransactionType.WRITE_OFF ? "WOFF" : "CRR";
        return prefix + "-" + LocalDate.now().format(CODE_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public ReturnDispositionStatusResponse getDispositionStatus(UUID commandId) {
        if (commandId == null) {
            throw new BadRequestException("CommandId must not be null");
        }
        Optional<InventoryInbox> inboxOpt = inboxRepository.findByCommandIdAndConsumerGroup(
                commandId.toString(),
                consumerGroup
        );
        if (inboxOpt.isEmpty()) {
            Optional<CustomerReturnInspection> inspectionOpt = inspectionRepository.findByCommandIdAndIsDeletedFalse(commandId);
            if (inspectionOpt.isPresent()) {
                CustomerReturnInspection inspection = inspectionOpt.get();
                if (inspection.getStatus() == CustomerReturnInspectionStatus.POSTED) {
                    return ReturnDispositionStatusResponse.builder()
                            .commandId(commandId)
                            .status(ReturnDispositionReconciliationStatus.PROCESSED)
                            .returnId(inspection.getReturnId())
                            .inspectionId(inspection.getId())
                            .processedAt(inspection.getPostedAt() != null ? inspection.getPostedAt() : inspection.getCreatedAt())
                            .build();
                } else {
                    return ReturnDispositionStatusResponse.builder()
                            .commandId(commandId)
                            .status(ReturnDispositionReconciliationStatus.PROCESSING)
                            .returnId(inspection.getReturnId())
                            .inspectionId(inspection.getId())
                            .failureReason("Inspection exists but is not yet posted (status: " + inspection.getStatus() + ")")
                            .build();
                }
            }
            return ReturnDispositionStatusResponse.builder()
                    .commandId(commandId)
                    .status(ReturnDispositionReconciliationStatus.NOT_FOUND)
                    .failureReason("Command not found in inventory inbox")
                    .build();
        }
        InventoryInbox inbox = inboxOpt.get();
        if (inbox.getStatus() == InboxStatus.FAILED || inbox.getStatus() == InboxStatus.DEAD) {
            return ReturnDispositionStatusResponse.builder()
                    .commandId(commandId)
                    .status(ReturnDispositionReconciliationStatus.FAILED)
                    .processedAt(inbox.getProcessedAt())
                    .failureCode("INBOX_FAILED")
                    .failureReason(inbox.getLastError() != null ? inbox.getLastError() : "Disposition processing failed in inventory")
                    .build();
        }
        if (inbox.getStatus() == InboxStatus.PROCESSED) {
            UUID inspectionId = null;
            if (inbox.getInspectionId() != null && !inbox.getInspectionId().isBlank()) {
                try {
                    inspectionId = UUID.fromString(inbox.getInspectionId());
                } catch (IllegalArgumentException ignored) {
                }
            }
            Optional<CustomerReturnInspection> inspectionOpt = Optional.empty();
            if (inspectionId != null) {
                inspectionOpt = inspectionRepository.findById(inspectionId);
            }
            if (inspectionOpt.isEmpty()) {
                inspectionOpt = inspectionRepository.findByCommandIdAndIsDeletedFalse(commandId);
            }
            if (inspectionOpt.isPresent()) {
                CustomerReturnInspection inspection = inspectionOpt.get();
                if (inspection.getStatus() == CustomerReturnInspectionStatus.POSTED) {
                    return ReturnDispositionStatusResponse.builder()
                            .commandId(commandId)
                            .status(ReturnDispositionReconciliationStatus.PROCESSED)
                            .returnId(inspection.getReturnId())
                            .inspectionId(inspection.getId())
                            .processedAt(inbox.getProcessedAt() != null ? inbox.getProcessedAt() : inspection.getPostedAt())
                            .build();
                } else {
                    return ReturnDispositionStatusResponse.builder()
                            .commandId(commandId)
                            .status(ReturnDispositionReconciliationStatus.PROCESSING)
                            .returnId(inspection.getReturnId())
                            .inspectionId(inspection.getId())
                            .failureReason("Inspection exists but is not yet posted (status: " + inspection.getStatus() + ")")
                            .build();
                }
            }
            return ReturnDispositionStatusResponse.builder()
                    .commandId(commandId)
                    .status(ReturnDispositionReconciliationStatus.PROCESSING)
                    .failureReason("Inspection not found or not yet posted")
                    .build();
        }
        return ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.PROCESSING)
                .failureReason("Disposition is currently being processed")
                .build();
    }
}
