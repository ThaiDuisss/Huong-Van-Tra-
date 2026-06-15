import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    Category,
    CategoryInput,
    PageResult
} from "../types/product.types";

export const categoryApi = {
    list(query: CatalogQuery = {}) {
        return apiClient.get<PageResult<Category>>("/inventory/categories", {
            query: {
                page: query.page,
                size: query.size,
                name: query.keyword,
                status: query.status
            }
        });
    },
    detail(id: string) {
        return apiClient.get<Category>(
            `/inventory/categories/${encodeURIComponent(id)}`
        );
    },
    create(input: CategoryInput) {
        return apiClient.post<Category, CategoryInput>(
            "/inventory/categories",
            input
        );
    },
    update(id: string, input: CategoryInput) {
        return apiClient.put<Category, CategoryInput>(
            `/inventory/categories/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: Category["status"]) {
        return apiClient.patch<Category, { status: Category["status"] }>(
            `/inventory/categories/${encodeURIComponent(id)}/status`,
            { status }
        );
    }
};
