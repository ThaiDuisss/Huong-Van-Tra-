import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../types/product.types";
import type {
    ImportBatch,
    ImportBatchQuery,
    ImportType,
    ImportPreviewResponse,
    SaveImportDraftRequest
} from "../types/import-batch.types";

/**
 * Tạo bộ API import-batch trỏ vào 1 basePath cụ thể (vd "/catalog/import-batches",
 * "/inventory/import-batches") — cho phép mỗi service dùng lại nguyên bộ UI (ImportBatchPanel,
 * ImportUploadPage, ImportBatchListPage) mà không cần copy lại logic gọi API.
 */
export function createImportBatchApi(basePath: string) {
    return {
        preview(type: ImportType, file: File) {
            const formData = new FormData();
            formData.append("file", file);
            return apiClient.upload<ImportPreviewResponse>(`${basePath}/preview`, formData, {
                query: { type }
            });
        },
        saveDraft(request: SaveImportDraftRequest) {
            return apiClient.post<ImportBatch, SaveImportDraftRequest>(`${basePath}/draft`, request);
        },
        create(type: ImportType, file: File) {
            const formData = new FormData();
            formData.append("file", file);
            return apiClient.upload<ImportBatch>(basePath, formData, {
                query: { type }
            });
        },
        list(query: ImportBatchQuery = {}) {
            return apiClient.get<PageResult<ImportBatch>>(basePath, {
                query
            });
        },
        detail(id: string) {
            return apiClient.get<ImportBatch>(`${basePath}/${encodeURIComponent(id)}`);
        },
        deleteBatch(id: string) {
            return apiClient.delete<void>(`${basePath}/${encodeURIComponent(id)}`);
        },
        updateItem(batchId: string, itemId: string, payload: Record<string, unknown>) {
            return apiClient.put<ImportBatch, { payload: Record<string, unknown> }>(
                `${basePath}/${encodeURIComponent(batchId)}/items/${encodeURIComponent(itemId)}`,
                { payload }
            );
        },
        deleteItem(batchId: string, itemId: string) {
            return apiClient.delete<ImportBatch>(
                `${basePath}/${encodeURIComponent(batchId)}/items/${encodeURIComponent(itemId)}`
            );
        },
        submit(id: string, expectedVersion: number) {
            return apiClient.post<ImportBatch, { expectedVersion: number }>(
                `${basePath}/${encodeURIComponent(id)}/submit`,
                { expectedVersion }
            );
        },
        approve(id: string, expectedVersion: number, note?: string) {
            return apiClient.post<ImportBatch, { note?: string; expectedVersion: number }>(
                `${basePath}/${encodeURIComponent(id)}/approve`,
                { note, expectedVersion }
            );
        },
        reject(id: string, expectedVersion: number, reason: string) {
            return apiClient.post<ImportBatch, { reason: string; expectedVersion: number }>(
                `${basePath}/${encodeURIComponent(id)}/reject`,
                { reason, expectedVersion }
            );
        }
    };
}

export type ImportBatchApi = ReturnType<typeof createImportBatchApi>;

export const importBatchApi = createImportBatchApi("/catalog/import-batches");
