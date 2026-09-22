import { apiClient } from "../../../api/api-client";
import type { CatalogQuery, PageResult, Unit, UnitInput } from "../types/product.types";

export const unitApi = {
    list(query: CatalogQuery = {}) {
        return apiClient.get<PageResult<Unit>>("/catalog/units", {
            query: {
                page: query.page,
                size: query.size,
                name: query.keyword,
                status: query.status,
                type: query.type
            }
        });
    },
    /** Fetch all units as a flat array (used by dropdowns / inventory filters). Backend luôn trả về dạng phân trang. */
    async listAll(): Promise<Unit[]> {
        const res = await apiClient.get<PageResult<Unit>>("/catalog/units", { query: { size: 500 } });
        return res.content ?? [];
    },
    listBases() {
        return apiClient.get<Unit[]>("/catalog/units/bases");
    },
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResult<{ id: string; name: string }>>("/catalog/units/lookup", {
            query: { search, page, size }
        });
    },
    detail(id: string) {
        return apiClient.get<Unit>(`/catalog/units/${encodeURIComponent(id)}`);
    },
    create(input: UnitInput) {
        return apiClient.post<Unit, UnitInput>("/catalog/units", input);
    },
    update(id: string, input: UnitInput) {
        return apiClient.put<Unit, UnitInput>(
            `/catalog/units/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: Unit["status"]) {
        return apiClient.patch<Unit, { status: Unit["status"] }>(
            `/catalog/units/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    delete(id: string) {
        return apiClient.delete<void>(`/catalog/units/${encodeURIComponent(id)}`);
    },
    export(query: { search?: string; keyword?: string; status?: string; type?: string } = {}) {
        return apiClient.download("/catalog/units/export", {
            query: {
                search: query.search ?? query.keyword,
                status: query.status,
                type: query.type
            }
        });
    },
    import(file: File) {
        const formData = new FormData();
        formData.append("file", file);
        return apiClient.upload<void>("/catalog/units/import", formData);
    },
    downloadTemplate() {
        return apiClient.download("/catalog/units/import/template");
    }
};
