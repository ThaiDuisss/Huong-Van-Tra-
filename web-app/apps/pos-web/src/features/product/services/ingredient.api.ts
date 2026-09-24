import { apiClient } from "../../../api/api-client";
import type { CatalogQuery, PageResult, Ingredient, IngredientInput } from "../types/product.types";

export interface IngredientQuery extends CatalogQuery {
    search?: string;
    supplierId?: string;
}

export const ingredientApi = {
    /** Dropdown chọn nguyên liệu — chỉ id + name, phân trang, chỉ nguyên liệu đang hoạt động. */
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResult<{ id: string; name: string }>>("/catalog/ingredients/lookup", {
            query: { search: search || undefined, status: "ACTIVE", page, size }
        });
    },
    /** Lấy tên nguyên liệu theo id (cho giá trị đã chọn sẵn trong dropdown). */
    lookupByIds(ids: string[]) {
        return apiClient.get<PageResult<{ id: string; name: string }>>("/catalog/ingredients/lookup", {
            query: { ids }
        });
    },
    list(query: IngredientQuery = {}) {
        return apiClient.get<PageResult<Ingredient>>("/catalog/ingredients", {
            query: {
                page: query.page,
                search: query.search ?? query.keyword,
                size: query.size,
                status: query.status,
                supplierId: query.supplierId
            }
        });
    },
    detail(id: string) {
        return apiClient.get<Ingredient>(`/catalog/ingredients/${encodeURIComponent(id)}`);
    },
    create(input: IngredientInput) {
        return apiClient.post<Ingredient, IngredientInput>("/catalog/ingredients", input);
    },
    update(id: string, input: IngredientInput) {
        return apiClient.put<Ingredient, IngredientInput>(
            `/catalog/ingredients/${encodeURIComponent(id)}`,
            input
        );
    },
    delete(id: string) {
        return apiClient.delete<void>(`/catalog/ingredients/${encodeURIComponent(id)}`);
    },
    updateStatus(id: string, status: Ingredient["status"]) {
        return apiClient.patch<Ingredient, { status: Ingredient["status"] }>(
            `/catalog/ingredients/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    export(query: IngredientQuery = {}) {
        return apiClient.download("/catalog/ingredients/export", {
            query: {
                search: query.search ?? query.keyword,
                status: query.status
            }
        });
    },
    downloadTemplate() {
        return apiClient.download("/catalog/ingredients/import/template");
    }
};
