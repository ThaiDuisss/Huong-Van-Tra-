import { apiClient } from "../../../api/api-client";
import type { PageResponse } from "../types/inventory.types";
import type {
    ShelfReplenishment,
    ShelfReplenishmentCreateInput,
    ShelfReplenishmentQuery,
    ShelfReplenishmentSummary,
    ShelfReplenishmentUpdateInput
} from "../types/shelfReplenishment.types";

const basePath = "/inventory/shelf-replenishments";

/** Phiếu nhập kệ: chuyển hàng từ kho lên kệ bán. */
export const shelfReplenishmentApi = {
    list(query: ShelfReplenishmentQuery = {}) {
        return apiClient.get<PageResponse<ShelfReplenishment>>(basePath, { query });
    },
    summary() {
        return apiClient.get<ShelfReplenishmentSummary>(`${basePath}/summary`);
    },
    detail(id: string) {
        return apiClient.get<ShelfReplenishment>(`${basePath}/${encodeURIComponent(id)}`);
    },
    create(input: ShelfReplenishmentCreateInput) {
        return apiClient.post<ShelfReplenishment, ShelfReplenishmentCreateInput>(basePath, input);
    },
    update(id: string, input: ShelfReplenishmentUpdateInput) {
        return apiClient.put<ShelfReplenishment, ShelfReplenishmentUpdateInput>(
            `${basePath}/${encodeURIComponent(id)}`,
            input
        );
    },
    submit(id: string) {
        return apiClient.post<ShelfReplenishment>(`${basePath}/${encodeURIComponent(id)}/submit`);
    },
    post(id: string) {
        return apiClient.post<ShelfReplenishment>(`${basePath}/${encodeURIComponent(id)}/post`);
    },
    /** Manager duyệt lại phiếu đã được POS xuất kho ngay khi bán. */
    review(id: string) {
        return apiClient.post<ShelfReplenishment>(`${basePath}/${encodeURIComponent(id)}/review`);
    },
    cancel(id: string, note?: string) {
        return apiClient.post<ShelfReplenishment>(
            `${basePath}/${encodeURIComponent(id)}/cancel`,
            undefined,
            { query: { note } }
        );
    }
};
