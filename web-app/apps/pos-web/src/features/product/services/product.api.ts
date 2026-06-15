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
    }
};
