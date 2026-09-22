package org.hvtra.commonservice.importing.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.commonservice.importing.domain.enums.ImportBatchStatus;
import org.hvtra.commonservice.importing.domain.enums.ImportType;
import org.hvtra.commonservice.importing.dto.request.SaveImportDraftRequest;
import org.hvtra.commonservice.importing.dto.response.ImportBatchItemResponse;
import org.hvtra.commonservice.importing.dto.response.ImportBatchResponse;
import org.hvtra.commonservice.importing.dto.response.ImportPreviewItemResponse;
import org.hvtra.commonservice.importing.dto.response.ImportPreviewResponse;
import org.hvtra.commonservice.importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.importing.entity.ImportBatch;
import org.hvtra.commonservice.importing.entity.ImportBatchItem;
import org.hvtra.commonservice.importing.entity.ImportBatchSequence;
import org.hvtra.commonservice.importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.importing.handler.ParsedImportRow;
import org.hvtra.commonservice.importing.repository.ImportBatchRepository;
import org.hvtra.commonservice.importing.repository.ImportBatchSequenceRepository;
import org.hvtra.commonservice.importing.service.ImportBatchService;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ImportBatchServiceImpl implements ImportBatchService {

    private static final String SEQUENCE_KEY = "IMPORT_BATCH";
    private static final TypeReference<List<ValidationErrorResponse>> ERRORS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};
    /** Các role được xem toàn bộ phiếu import; role khác chỉ được xem phiếu do chính mình tạo. */
    private static final Set<String> ELEVATED_ROLES = Set.of("ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER");

    private final ImportBatchRepository repository;
    private final ImportBatchSequenceRepository sequenceRepository;
    private final ObjectMapper objectMapper;
    private final Map<ImportType, ImportTypeHandler> handlers;

    public ImportBatchServiceImpl(
            ImportBatchRepository repository,
            ImportBatchSequenceRepository sequenceRepository,
            ObjectMapper objectMapper,
            List<ImportTypeHandler> handlerList) {
        this.repository = repository;
        this.sequenceRepository = sequenceRepository;
        this.objectMapper = objectMapper;
        this.handlers = handlerList.stream().collect(Collectors.toMap(ImportTypeHandler::type, Function.identity()));
    }

    @Override
    public ImportPreviewResponse preview(ImportType type, MultipartFile file) {
        ImportTypeHandler handler = handlerFor(type);
        List<ParsedImportRow> rows = handler.parseExcel(file);
        if (rows.isEmpty()) {
            throw new BusinessException("EMPTY_IMPORT_FILE", "File Excel không có dòng dữ liệu hợp lệ nào để import.");
        }

        List<List<ValidationErrorResponse>> allErrors =
                handler.validateAll(rows.stream().map(ParsedImportRow::payload).toList());

        List<ImportPreviewItemResponse> items = new ArrayList<>(rows.size());
        int validCount = 0;
        int errorCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            ParsedImportRow row = rows.get(i);
            List<ValidationErrorResponse> errors = allErrors.get(i);
            if (errors == null || errors.isEmpty()) {
                validCount++;
            } else {
                errorCount++;
            }
            items.add(new ImportPreviewItemResponse(
                    UUID.randomUUID().toString(),
                    row.rowIndex(),
                    row.payload(),
                    errors != null ? errors : Collections.emptyList(),
                    row.occurrenceCount()
            ));
        }

        return new ImportPreviewResponse(
                type,
                file.getOriginalFilename(),
                rows.size(),
                validCount,
                errorCount,
                items
        );
    }

    @Override
    @Transactional
    public ImportBatchResponse saveDraft(SaveImportDraftRequest request) {
        ImportTypeHandler handler = handlerFor(request.type());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("EMPTY_BATCH", "Phiếu import chưa có dòng dữ liệu nào.");
        }

        ImportBatch batch = new ImportBatch();
        batch.setImportType(request.type());
        batch.setBatchCode(generateBatchCode());
        batch.setStatus(ImportBatchStatus.DRAFT);
        batch.setSourceFileName(request.sourceFileName());

        List<List<ValidationErrorResponse>> allErrors =
                handler.validateAll(request.items().stream().map(SaveImportDraftRequest.DraftItem::payload).toList());

        for (int i = 0; i < request.items().size(); i++) {
            SaveImportDraftRequest.DraftItem draftItem = request.items().get(i);
            ImportBatchItem item = new ImportBatchItem();
            item.setRowIndex(draftItem.rowIndex() != null ? draftItem.rowIndex() : (i + 1));
            item.setPayload(writeJson(draftItem.payload()));
            item.setValidationErrors(writeJson(allErrors.get(i)));
            item.setOccurrenceCount(draftItem.occurrenceCount() != null ? draftItem.occurrenceCount() : 1);
            batch.addItem(item);
        }

        return toResponse(repository.save(batch));
    }

    @Override
    @Transactional
    public ImportBatchResponse createFromExcel(ImportType type, MultipartFile file) {
        ImportTypeHandler handler = handlerFor(type);
        List<ParsedImportRow> rows = handler.parseExcel(file);
        if (rows.isEmpty()) {
            throw new BusinessException("EMPTY_IMPORT_FILE", "File Excel không có dòng dữ liệu hợp lệ nào để import.");
        }

        ImportBatch batch = new ImportBatch();
        batch.setImportType(type);
        batch.setBatchCode(generateBatchCode());
        batch.setStatus(ImportBatchStatus.DRAFT);
        batch.setSourceFileName(file.getOriginalFilename());

        // Validate cả loạt 1 lần (không lặp handler.validate() từng dòng) để handler có thể gộp
        // tra cứu tồn tại theo id (product/unit/ingredient...) thành vài query thay vì N query riêng lẻ.
        List<List<ValidationErrorResponse>> allErrors = handler.validateAll(rows.stream().map(ParsedImportRow::payload).toList());
        for (int i = 0; i < rows.size(); i++) {
            ParsedImportRow row = rows.get(i);
            ImportBatchItem item = new ImportBatchItem();
            item.setRowIndex(row.rowIndex());
            item.setPayload(writeJson(row.payload()));
            item.setValidationErrors(writeJson(allErrors.get(i)));
            item.setOccurrenceCount(row.occurrenceCount());
            batch.addItem(item);
        }

        return toResponse(repository.save(batch));
    }

    @Override
    public ImportBatchResponse findById(UUID id) {
        return toResponse(getBatchWithItems(id));
    }

    @Override
    public Page<ImportBatchResponse> findAll(ImportType type, ImportBatchStatus status, Pageable pageable) {
        boolean viewAll = canViewAllBatches();
        UUID currentUserId = getCurrentUserId();
        Specification<ImportBatch> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (type != null) predicates.add(cb.equal(root.get("importType"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (!viewAll) {
                predicates.add(currentUserId != null ? cb.equal(root.get("createdBy"), currentUserId) : cb.disjunction());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        // Dùng bản summary (không đọc batch.getItems()) để tránh N+1 khi map danh sách phân trang.
        return repository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Transactional
    public ImportBatchResponse updateItem(UUID batchId, UUID itemId, Map<String, Object> payload) {
        ImportBatch batch = getBatchWithItems(batchId);
        requireEditableOrReopen(batch);
        ImportBatchItem item = findItem(batch, itemId);
        ImportTypeHandler handler = handlerFor(batch.getImportType());

        item.setPayload(writeJson(payload));
        item.setValidationErrors(writeJson(handler.validate(payload)));

        return toResponse(repository.save(batch));
    }

    @Override
    @Transactional
    public ImportBatchResponse deleteItem(UUID batchId, UUID itemId) {
        ImportBatch batch = getBatchWithItems(batchId);
        requireEditableOrReopen(batch);
        ImportBatchItem item = findItem(batch, itemId);
        batch.getItems().remove(item);
        return toResponse(repository.save(batch));
    }

    @Override
    @Transactional
    public void deleteBatch(UUID batchId) {
        ImportBatch batch = getBatchWithItems(batchId);
        if (batch.getStatus() != ImportBatchStatus.DRAFT && batch.getStatus() != ImportBatchStatus.REJECTED) {
            throw new BusinessException("INVALID_STATE",
                    "Chỉ có thể xóa phiếu import ở trạng thái Nháp hoặc Bị từ chối (hiện tại: " + batch.getStatus() + ")");
        }
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(batch.getCreatedBy())) {
            throw new BusinessException("FORBIDDEN", "Bạn chỉ có thể xóa phiếu import do chính mình tạo.", HttpStatus.FORBIDDEN);
        }
        batch.setIsDeleted(true);
        repository.save(batch);
    }

    @Override
    @Transactional
    public ImportBatchResponse submit(UUID batchId, Long expectedVersion) {
        ImportBatch batch = getBatchWithItems(batchId);
        checkVersion(batch, expectedVersion);
        if (batch.getStatus() != ImportBatchStatus.DRAFT) {
            throw new BusinessException("INVALID_STATE",
                    "Chỉ có thể gửi duyệt phiếu import ở trạng thái DRAFT (hiện tại: " + batch.getStatus() + ")");
        }
        if (batch.getItems().isEmpty()) {
            throw new BusinessException("EMPTY_BATCH", "Phiếu import chưa có dòng dữ liệu nào");
        }
        boolean hasErrors = batch.getItems().stream().anyMatch(this::hasErrors);
        if (hasErrors) {
            throw new BusinessException("HAS_INVALID_ITEMS", "Còn dòng dữ liệu chưa hợp lệ — vui lòng sửa hết lỗi trước khi gửi duyệt");
        }

        batch.setStatus(ImportBatchStatus.PENDING_APPROVAL);
        batch.setSubmittedBy(getCurrentUserId());
        batch.setSubmittedAt(Instant.now());
        return toResponse(repository.save(batch));
    }

    @Override
    @Transactional
    public ImportBatchResponse approve(UUID batchId, String note, Long expectedVersion) {
        ImportBatch batch = getBatchWithItems(batchId);
        checkVersion(batch, expectedVersion);
        if (batch.getStatus() != ImportBatchStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATE",
                    "Chỉ có thể duyệt phiếu import ở trạng thái PENDING_APPROVAL (hiện tại: " + batch.getStatus() + ")");
        }

        ImportTypeHandler handler = handlerFor(batch.getImportType());

        // 1. Validate lại toàn bộ trước — nếu còn bất kỳ lỗi nào thì dừng ngay, KHÔNG commit dòng nào cả.
        //    Gọi validateAll() 1 lần cho cả batch (không lặp validate() từng item) để tránh N+1 khi
        //    handler cần tra cứu tồn tại theo id (product/unit/ingredient...).
        List<ImportBatchItem> items = batch.getItems();
        List<List<ValidationErrorResponse>> allErrors =
                handler.validateAll(items.stream().map(this::readPayload).toList());
        for (int i = 0; i < items.size(); i++) {
            ImportBatchItem item = items.get(i);
            List<ValidationErrorResponse> errors = allErrors.get(i);
            item.setValidationErrors(writeJson(errors));
            if (!errors.isEmpty()) {
                throw new BusinessException("ITEM_INVALID",
                        "Dòng " + item.getRowIndex() + " không còn hợp lệ (" + errors.get(0).message()
                                + ") — vui lòng sửa lại trước khi duyệt. Chưa có dữ liệu nào được ghi.");
            }
        }

        // 2. Tất cả hợp lệ -> commit từng dòng thật, cùng transaction với approve() này
        //    (service.create() ở handler cũng @Transactional, mặc định Propagation.REQUIRED nên sẽ tham gia
        //     transaction hiện tại — 1 dòng lỗi giữa chừng sẽ rollback toàn bộ batch, không ghi nửa vời).
        for (ImportBatchItem item : batch.getItems()) {
            UUID createdId = handler.commit(readPayload(item));
            item.setCreatedEntityId(createdId);
        }

        batch.setStatus(ImportBatchStatus.APPROVED);
        batch.setApprovedBy(getCurrentUserId());
        batch.setApprovedAt(Instant.now());
        batch.setNote(note);
        return toResponse(repository.save(batch));
    }

    @Override
    @Transactional
    public ImportBatchResponse reject(UUID batchId, String reason, Long expectedVersion) {
        ImportBatch batch = getBatchWithItems(batchId);
        checkVersion(batch, expectedVersion);
        if (batch.getStatus() != ImportBatchStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATE",
                    "Chỉ có thể từ chối phiếu import ở trạng thái PENDING_APPROVAL (hiện tại: " + batch.getStatus() + ")");
        }

        batch.setStatus(ImportBatchStatus.REJECTED);
        batch.setRejectedBy(getCurrentUserId());
        batch.setRejectedAt(Instant.now());
        batch.setRejectionReason(reason);
        return toResponse(repository.save(batch));
    }

    // ---------------------------------------------------------------------

    private ImportTypeHandler handlerFor(ImportType type) {
        ImportTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new BusinessException("UNSUPPORTED_IMPORT_TYPE", "Loại import chưa được hỗ trợ: " + type);
        }
        return handler;
    }

    private ImportBatch getBatchWithItems(UUID id) {
        return repository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu import: " + id));
    }

    private ImportBatchItem findItem(ImportBatch batch, UUID itemId) {
        return batch.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng import: " + itemId));
    }

    /**
     * Chỉ DRAFT/REJECTED được sửa. Nếu đang REJECTED, tự chuyển về DRAFT trước khi
     * áp dụng thay đổi (chuyển trạng thái tường minh, không phải side-effect ngầm).
     */
    private void requireEditableOrReopen(ImportBatch batch) {
        if (batch.getStatus() == ImportBatchStatus.REJECTED) {
            batch.setStatus(ImportBatchStatus.DRAFT);
            return;
        }
        if (batch.getStatus() != ImportBatchStatus.DRAFT) {
            throw new BusinessException("INVALID_STATE",
                    "Không thể chỉnh sửa phiếu import đang ở trạng thái " + batch.getStatus());
        }
    }

    private void checkVersion(ImportBatch batch, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(batch.getVersion())) {
            throw new BusinessException("CONFLICT",
                    "Phiếu import đã bị thay đổi bởi người khác, vui lòng tải lại trang trước khi thao tác.",
                    HttpStatus.CONFLICT);
        }
    }

    private boolean hasErrors(ImportBatchItem item) {
        return !readErrors(item).isEmpty();
    }

    private String generateBatchCode() {
        ImportBatchSequence seq = sequenceRepository.findBySequenceKeyWithLock(SEQUENCE_KEY)
                .orElseGet(() -> {
                    ImportBatchSequence newSeq = new ImportBatchSequence();
                    newSeq.setSequenceKey(SEQUENCE_KEY);
                    newSeq.setCurrentValue(0L);
                    return sequenceRepository.saveAndFlush(newSeq);
                });
        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        sequenceRepository.saveAndFlush(seq);
        return String.format("IMP-%05d", nextVal);
    }

    private UUID getCurrentUserId() {
        try {
            String userId = SecurityUtils.currentUserId();
            return userId == null || userId.isBlank() ? null : UUID.fromString(userId);
        } catch (AccessDeniedException | IllegalArgumentException ignored) {
            return null;
        }
    }

    /** ADMIN/BRANCH_MANAGER/INVENTORY_MANAGER xem toàn bộ; role khác chỉ xem phiếu do chính mình tạo. */
    private boolean canViewAllBatches() {
        try {
            return SecurityUtils.currentRoles().stream().anyMatch(ELEVATED_ROLES::contains);
        } catch (AccessDeniedException ignored) {
            return false;
        }
    }

    private Map<String, Object> readPayload(ImportBatchItem item) {
        try {
            return objectMapper.readValue(item.getPayload(), PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được payload của dòng import: " + e.getMessage(), e);
        }
    }

    private List<ValidationErrorResponse> readErrors(ImportBatchItem item) {
        String json = item.getValidationErrors();
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, ERRORS_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được dữ liệu import: " + e.getMessage(), e);
        }
    }

    private ImportBatchItemResponse toItemResponse(ImportBatchItem item) {
        return new ImportBatchItemResponse(
                item.getId(),
                item.getRowIndex(),
                readPayload(item),
                readErrors(item),
                item.getOccurrenceCount(),
                item.getCreatedEntityId()
        );
    }

    private ImportBatchResponse toResponse(ImportBatch batch) {
        List<ImportBatchItemResponse> items = batch.getItems().stream().map(this::toItemResponse).toList();
        return build(batch, items);
    }

    private ImportBatchResponse toSummaryResponse(ImportBatch batch) {
        return build(batch, Collections.emptyList());
    }

    private ImportBatchResponse build(ImportBatch batch, List<ImportBatchItemResponse> items) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getImportType(),
                batch.getBatchCode(),
                batch.getStatus(),
                batch.getVersion(),
                batch.getSourceFileName(),
                batch.getNote(),
                batch.getCreatedBy(),
                batch.getSubmittedBy(),
                batch.getSubmittedAt(),
                batch.getApprovedBy(),
                batch.getApprovedAt(),
                batch.getRejectedBy(),
                batch.getRejectedAt(),
                batch.getRejectionReason(),
                items,
                batch.getCreatedAt(),
                batch.getUpdatedAt()
        );
    }
}
