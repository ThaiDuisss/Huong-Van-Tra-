import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../types/product.types";
import type {
    ImportBatch,
    ImportBatchQuery,
    ImportType
} from "../types/import-batch.types";

export const importBatchApi = {
    create(type: ImportType, file: File) {
        const formData = new FormData();
        formData.append("file", file);
        return apiClient.upload<ImportBatch>("/catalog/import-batches", formData, {
            query: { type }
        });
    },
    list(query: ImportBatchQuery = {}) {
        return apiClient.get<PageResult<ImportBatch>>("/catalog/import-batches", {
            query
        });
    },
    detail(id: string) {
        return apiClient.get<ImportBatch>(`/catalog/import-batches/${encodeURIComponent(id)}`);
    },
    deleteBatch(id: string) {
        return apiClient.delete<void>(`/catalog/import-batches/${encodeURIComponent(id)}`);
    },
    updateItem(batchId: string, itemId: string, payload: Record<string, unknown>) {
        return apiClient.put<ImportBatch, { payload: Record<string, unknown> }>(
            `/catalog/import-batches/${encodeURIComponent(batchId)}/items/${encodeURIComponent(itemId)}`,
            { payload }
        );
    },
    deleteItem(batchId: string, itemId: string) {
        return apiClient.delete<ImportBatch>(
            `/catalog/import-batches/${encodeURIComponent(batchId)}/items/${encodeURIComponent(itemId)}`
        );
    },
    submit(id: string, expectedVersion: number) {
        return apiClient.post<ImportBatch, { expectedVersion: number }>(
            `/catalog/import-batches/${encodeURIComponent(id)}/submit`,
            { expectedVersion }
        );
    },
    approve(id: string, expectedVersion: number, note?: string) {
        return apiClient.post<ImportBatch, { note?: string; expectedVersion: number }>(
            `/catalog/import-batches/${encodeURIComponent(id)}/approve`,
            { note, expectedVersion }
        );
    },
    reject(id: string, expectedVersion: number, reason: string) {
        return apiClient.post<ImportBatch, { reason: string; expectedVersion: number }>(
            `/catalog/import-batches/${encodeURIComponent(id)}/reject`,
            { reason, expectedVersion }
        );
    }
};
