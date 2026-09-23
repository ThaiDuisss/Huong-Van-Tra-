package org.hvtra.commonservice.common_importing.service;

import org.hvtra.commonservice.common_importing.enums.ImportBatchStatus;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.request.SaveImportDraftRequest;
import org.hvtra.commonservice.common_importing.dto.response.ImportBatchResponse;
import org.hvtra.commonservice.common_importing.dto.response.ImportPreviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface ImportBatchService {
    /** Phân tích file Excel và validate với Database, KHÔNG lưu vào DB, KHÔNG sinh batchCode. */
    ImportPreviewResponse preview(ImportType type, MultipartFile file);

    /** Lưu bản nháp (DRAFT) vào DB từ danh sách dòng do người dùng xác nhận trên UI. */
    ImportBatchResponse saveDraft(SaveImportDraftRequest request);

    ImportBatchResponse createFromExcel(ImportType type, MultipartFile file);

    ImportBatchResponse findById(UUID id);

    Page<ImportBatchResponse> findAll(ImportType type, ImportBatchStatus status, Pageable pageable);

    ImportBatchResponse updateItem(UUID batchId, UUID itemId, Map<String, Object> payload);

    ImportBatchResponse deleteItem(UUID batchId, UUID itemId);

    /** Xóa cả phiếu import (không phải xóa 1 dòng) — chỉ cho phép khi DRAFT/REJECTED và người gọi là người tạo. */
    void deleteBatch(UUID batchId);

    ImportBatchResponse submit(UUID batchId, Long expectedVersion);

    ImportBatchResponse approve(UUID batchId, String note, Long expectedVersion);

    ImportBatchResponse reject(UUID batchId, String reason, Long expectedVersion);
}
