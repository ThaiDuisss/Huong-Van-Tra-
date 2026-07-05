import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    Category,
    CategoryInput,
    PageResult
} from "../types/product.types";

export const categoryApi = {
    list(query: CatalogQuery = {}) {
        return apiClient.get<PageResult<Category>>("/catalog/categories", {
            query: {
                page: query.page,
                size: query.size,
                name: query.keyword,
                status: query.status
            }
        });
    },
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResult<{ id: string; name: string }>>("/catalog/categories/lookup", {
            query: { search, page, size }
        });
    },
    detail(id: string) {
        return apiClient.get<Category>(
            `/catalog/categories/${encodeURIComponent(id)}`
        );
    },
    create(input: CategoryInput) {
        return apiClient.post<Category, CategoryInput>(
            "/catalog/categories",
            input
        );
    },
    update(id: string, input: CategoryInput) {
        return apiClient.put<Category, CategoryInput>(
            `/catalog/categories/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: Category["status"]) {
        return apiClient.patch<Category, { status: Category["status"] }>(
            `/catalog/categories/${encodeURIComponent(id)}/status`,
            { status }
        );
    }
};
