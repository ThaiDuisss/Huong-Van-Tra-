import { apiClient } from "../../../api/api-client";
import type { CatalogQuery, PageResult, Ingredient, IngredientInput } from "../types/product.types";

export interface IngredientQuery extends CatalogQuery {
    search?: string;
    supplierId?: string;
}

export const ingredientApi = {
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
    import(file: File) {
        const formData = new FormData();
        formData.append("file", file);
        return apiClient.upload<void>("/catalog/ingredients/import", formData);
    },
    downloadTemplate() {
        return apiClient.download("/catalog/ingredients/import/template");
    }
};
