package org.hvtra.commonservice.importing.service;

import org.hvtra.commonservice.importing.domain.enums.ImportBatchStatus;
import org.hvtra.commonservice.importing.domain.enums.ImportType;
import org.hvtra.commonservice.importing.dto.response.ImportBatchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface ImportBatchService {
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
