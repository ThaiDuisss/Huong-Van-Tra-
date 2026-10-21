import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    PageResult,
    Product,
    ProductInput,
    ProductSummary
} from "../types/product.types";

export interface ProductQuery extends CatalogQuery {
    categoryId?: string;
}

export const productApi = {
    list(query: ProductQuery = {}) {
        return apiClient.get<PageResult<ProductSummary>>("/catalog/products", {
            query: {
                page: query.page,
                size: query.size,
                name: query.keyword,
                categoryId: query.categoryId,
                status: query.status
            }
        });
    },
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResult<{ id: string; name: string }>>("/catalog/products/lookup", {
            query: { search, page, size }
        });
    },
    detail(id: string) {
        return apiClient.get<Product>(
            `/catalog/products/${encodeURIComponent(id)}`
        );
    },
    create(input: ProductInput) {
        return apiClient.post<Product, ProductInput>(
            "/catalog/products",
            input
        );
    },
    update(id: string, input: ProductInput) {
        return apiClient.put<Product, ProductInput>(
            `/catalog/products/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: ProductSummary["status"]) {
        return apiClient.patch<
            ProductSummary,
            { status: ProductSummary["status"] }
        >(
            `/catalog/products/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    export() {
        return apiClient.download("/catalog/products/export");
    },
    downloadTemplate() {
        return apiClient.download("/catalog/products/import/template");
    },
    delete(id: string) {
        return apiClient.delete<void>(`/catalog/products/${encodeURIComponent(id)}`);
    }
};
