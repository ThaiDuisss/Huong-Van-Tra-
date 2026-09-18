package org.hvtra.iventoryservice.customerreturn.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcDispositionReportRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcSummaryBatchRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionLineResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryLineResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionBatchAllocation;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnCondition;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnItemResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;
import org.hvtra.commonservice.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final InventoryInboxRepository inboxRepository;

    @Value("${inventory.order.events.return-disposition.group-id:inventory-return-disposition}")
    private String consumerGroup;

    @Override
    @Transactional
    public CustomerReturnInspectionResponse createInspection(
            UUID returnId,
            CreateCustomerReturnInspectionRequest request) {
        OrderReturnResponse orderReturn = verifiedReceivedReturn(returnId);
        Map<UUID, OrderReturnItemResponse> returnItems = returnItemsById(orderReturn);
        Map<UUID, BigDecimal> postedQuantities = postedQuantities(returnId);
        Set<UUID> requestedReturnItems = new HashSet<>();
        UUID inspectionBranchId = resolveInspectionBranch(orderReturn, request.lines());

        CustomerReturnInspection inspection = new CustomerReturnInspection();
        inspection.setInspectionCode(generateInspectionCode());
        inspection.setReturnId(orderReturn.id());
        inspection.setReturnCode(orderReturn.returnCode());
        inspection.setBranchId(inspectionBranchId);
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
            validateWithinReturnedQuantity(returnItem, postedQuantities.get(returnItem.id()), lineRequest.quantity());
            validateConditionResult(returnItem.condition(), lineRequest.result(), lineRequest.note(), request.note());

            InventoryItem item = activeProductVariantItem(returnItem.productVariantId());
            InventoryLocation destination = resolveDestination(
                    inspectionBranchId,
                    lineRequest.result(),
                    lineRequest.destinationLocationId());

            CustomerReturnInspectionLine line = new CustomerReturnInspectionLine();
            line.setReturnItemId(returnItem.id());
            line.setOrderItemId(returnItem.orderItemId());
            line.setProductVariantId(returnItem.productVariantId());
            line.setInventoryItem(item);
            line.setQuantity(lineRequest.quantity());
            line.setResult(lineRequest.result());
            line.setDestinationLocation(destination);
            line.setNote(lineRequest.note());
            inspection.addLine(line);
        }

        return toResponse(inspectionRepository.saveAndFlush(inspection));
    }

    @Override
    @Transactional
    public CustomerReturnInspectionResponse postInspection(UUID inspectionId) {
        CustomerReturnInspection inspectionSnapshot = inspectionRepository
                .findByIdAndIsDeletedFalse(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));
        OrderReturnResponse orderReturn = verifiedReceivedReturn(inspectionSnapshot.getReturnId());
        Map<UUID, OrderReturnItemResponse> returnItems = returnItemsById(orderReturn);

        CustomerReturnInspection inspection = inspectionRepository
                .findByReturnIdForUpdate(inspectionSnapshot.getReturnId())
                .stream()
                .filter(candidate -> candidate.getId().equals(inspectionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Customer return inspection", inspectionId));
        if (inspection.getStatus() == CustomerReturnInspectionStatus.POSTED) {
            throw new BadRequestException("Customer return inspection is already posted: " + inspectionId);
        }
        if (inspection.getStatus() == CustomerReturnInspectionStatus.CANCELLED) {
            throw new BadRequestException("Cancelled customer return inspection cannot be posted: " + inspectionId);
        }

        Map<UUID, BigDecimal> postedQuantities = postedQuantities(inspection.getReturnId());
        for (CustomerReturnInspectionLine line : inspection.getLines()) {
            OrderReturnItemResponse returnItem = returnItems.get(line.getReturnItemId());
            if (returnItem == null) {
                throw new BadRequestException("Return item no longer exists in Order-Service: " + line.getReturnItemId());
            }
            validateWithinReturnedQuantity(returnItem, postedQuantities.get(returnItem.id()), line.getQuantity());
            postLine(inspection, line);
            line.setPosted(true);
        }

        inspection.setStatus(CustomerReturnInspectionStatus.POSTED);
        inspection.setPostedAt(Instant.now());
        return toResponse(inspectionRepository.saveAndFlush(inspection));
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
                lines);
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

        return inspectionRepository.sumDispositionByReturnIds(
                returnIds,
                CustomerReturnInspectionStatus.POSTED
        );
    }

    private void postLine(CustomerReturnInspection inspection, CustomerReturnInspectionLine line) {
        if (line.getResult() == CustomerReturnQcResult.WRITE_OFF) {
            InventoryTransaction transaction = createTransaction(
                    InventoryTransactionType.WRITE_OFF,
                    inspection.getBranchId(),
                    inspection.getReturnId());
            line.setTransactionId(transactionRepository.saveAndFlush(transaction).getId());
            return;
        }

        InventoryLocation location = validateDestinationForPosting(
                inspection.getBranchId(),
                line.getResult(),
                line.getDestinationLocation());
        InventoryBatch batch = defaultBatch(line.getInventoryItem());
        InventoryBalance balance = balanceRepository
                .findForUpdateByBatchId(batch.getId(), location.getId())
                .orElseGet(() -> newBalance(line.getInventoryItem(), batch, location));

        BigDecimal beforeOnHand = nullSafe(balance.getQuantityOnHand());
        BigDecimal afterOnHand = beforeOnHand.add(line.getQuantity());
        balance.setQuantityOnHand(afterOnHand);

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
        Map<UUID, BigDecimal> totals = new HashMap<>();
        inspectionRepository.findByReturnIdAndStatus(returnId, CustomerReturnInspectionStatus.POSTED)
                .stream()
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
            UUID branchId,
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
        return validateDestinationForPosting(branchId, result, location);
    }

    private UUID resolveInspectionBranch(
            OrderReturnResponse orderReturn,
            List<CreateCustomerReturnInspectionLineRequest> lines) {
        Optional<UUID> destinationBranchId = lines.stream()
                .map(CreateCustomerReturnInspectionLineRequest::destinationLocationId)
                .filter(Objects::nonNull)
                .findFirst()
                .map(destinationLocationId -> locationRepository.findById(destinationLocationId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Inventory location",
                                destinationLocationId))
                        .getBranchId());
        if (destinationBranchId.isPresent()) {
            return destinationBranchId.get();
        }
        if (orderReturn.branchId() != null) {
            return orderReturn.branchId();
        }

        List<UUID> activeBranchIds = locationRepository.findDistinctBranchIdsByStatus(InventoryStatus.ACTIVE);
        if (activeBranchIds.size() == 1) {
            return activeBranchIds.getFirst();
        }
        if (activeBranchIds.isEmpty()) {
            throw new BadRequestException("No active inventory location is configured");
        }
        throw new BadRequestException("Cannot resolve the single inventory branch from active locations");
    }

    private InventoryLocation validateDestinationForPosting(
            UUID branchId,
            CustomerReturnQcResult result,
            InventoryLocation location) {
        if (location == null) {
            throw new BadRequestException("destinationLocationId is required for " + result);
        }
        if (location.getStatus() != InventoryStatus.ACTIVE) {
            throw new BadRequestException("Inventory location is inactive: " + location.getId());
        }
        if (!location.getBranchId().equals(branchId)) {
            throw new BadRequestException("Inventory location does not belong to return branch: " + location.getId());
        }
        if (result == CustomerReturnQcResult.RESTOCK && !InventoryLocationSaleability.isSaleable(location)) {
            throw new BadRequestException("RESTOCK destination must be an active saleable location");
        }
        if (result == CustomerReturnQcResult.QUARANTINE && location.getType() != InventoryLocationType.QUARANTINE) {
            throw new BadRequestException("QUARANTINE destination must be a QUARANTINE location");
        }
        if (result == CustomerReturnQcResult.DAMAGED && location.getType() != InventoryLocationType.DAMAGED) {
            throw new BadRequestException("DAMAGED destination must be a DAMAGED location");
        }
        return location;
    }

    private void validateConditionResult(
            OrderReturnCondition condition,
            CustomerReturnQcResult result,
            String lineNote,
            String inspectionNote) {
        if (result == CustomerReturnQcResult.QUARANTINE) {
            throw new BadRequestException("QUARANTINE is no longer supported for customer return QC");
        }
        if (condition == OrderReturnCondition.SEALED) {
            if (result == CustomerReturnQcResult.WRITE_OFF && !hasReasonableNote(lineNote, inspectionNote)) {
                throw new BadRequestException("WRITE_OFF for SEALED return item requires a reason note");
            }
            if (result == CustomerReturnQcResult.DAMAGED && !hasReasonableNote(lineNote, inspectionNote)) {
                throw new BadRequestException("DAMAGED for SEALED return item requires a reason note");
            }
            return;
        }
        if (result == CustomerReturnQcResult.RESTOCK) {
            throw new BadRequestException("RESTOCK is not allowed for return condition " + condition);
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
                lines);
    }

    private QcProgress buildQcProgress(List<CustomerReturnInspection> inspections) {
        CustomerReturnInspection latestInspection = inspections.isEmpty() ? null : inspections.getFirst();
        Map<UUID, BigDecimal> postedQuantities = new HashMap<>();
        Map<UUID, Map<CustomerReturnQcResult, BigDecimal>> dispositionsByItem = new HashMap<>();

        inspections.stream()
                .filter(inspection -> inspection.getStatus() == CustomerReturnInspectionStatus.POSTED)
                .flatMap(inspection -> inspection.getLines().stream())
                .filter(line -> Boolean.TRUE.equals(line.getPosted()))
                .forEach(line -> {
                    postedQuantities.merge(line.getReturnItemId(), line.getQuantity(), BigDecimal::add);
                    dispositionsByItem
                            .computeIfAbsent(
                                    line.getReturnItemId(),
                                    ignored -> new EnumMap<>(CustomerReturnQcResult.class)
                            )
                            .merge(line.getResult(), line.getQuantity(), BigDecimal::add);
                });

        return new QcProgress(
                latestInspection == null ? null : latestInspection.getId(),
                latestInspection == null ? null : latestInspection.getStatus(),
                postedQuantities,
                dispositionsByItem
        );
    }

    private record QcProgress(
            UUID inspectionId,
            CustomerReturnInspectionStatus inspectionStatus,
            Map<UUID, BigDecimal> postedQuantities,
            Map<UUID, Map<CustomerReturnQcResult, BigDecimal>> dispositionsByItem
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
                            entry.getKey(),
                            entry.getValue()
                    ))
                    .toList();
        }
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
                inspection.getPostedAt(),
                inspection.getNote(),
                inspection.getLines().stream().map(this::toLineResponse).toList());
    }

    private CustomerReturnInspectionLineResponse toLineResponse(CustomerReturnInspectionLine line) {
        InventoryLocation destination = line.getDestinationLocation();
        return new CustomerReturnInspectionLineResponse(
                line.getId(),
                line.getReturnItemId(),
                line.getOrderItemId(),
                line.getProductVariantId(),
                line.getInventoryItem().getId(),
                line.getQuantity(),
                line.getResult(),
                destination == null ? null : destination.getId(),
                line.getNote(),
                line.getPosted(),
                line.getTransactionId());
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
            if (inspectionId != null) {
                Optional<CustomerReturnInspection> inspectionOpt = inspectionRepository.findById(inspectionId);
                if (inspectionOpt.isPresent()) {
                    CustomerReturnInspection inspection = inspectionOpt.get();
                    if (inspection.getStatus() == CustomerReturnInspectionStatus.POSTED) {
                        return ReturnDispositionStatusResponse.builder()
                                .commandId(commandId)
                                .status(ReturnDispositionReconciliationStatus.PROCESSED)
                                .returnId(inspection.getReturnId())
                                .inspectionId(inspection.getId())
                                .processedAt(inbox.getProcessedAt())
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
            }
            return ReturnDispositionStatusResponse.builder()
                    .commandId(commandId)
                    .status(ReturnDispositionReconciliationStatus.PROCESSED)
                    .inspectionId(inspectionId)
                    .processedAt(inbox.getProcessedAt())
                    .build();
        }
        return ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.PROCESSING)
                .failureReason("Disposition is currently being processed")
                .build();
    }
}
