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
    /** Fetch all units as a flat array (used by dropdowns / inventory filters) */
    listAll() {
        return apiClient.get<Unit[]>("/catalog/units/all");
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
    }
};
