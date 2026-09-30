import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    PageResult,
    ProductVariant,
    ProductVariantInput,
    SellableConfigInput
} from "../types/product.types";

export interface ProductVariantQuery extends CatalogQuery {
    productId?: string;
    categoryId?: string;
    sku?: string;
    variantName?: string;
}

export const productVariantApi = {
    list(query: ProductVariantQuery = {}) {
        return apiClient.get<PageResult<ProductVariant>>(
            "/catalog/product-variants",
            {
                query: {
                    page: query.page,
                    size: query.size,
                    keyword: query.sku ?? query.keyword,
                    productId: query.productId,
                    categoryId: query.categoryId,
                    status: query.status
                }
            }
        );
    },
    listByProduct(productId: string, query: CatalogQuery = {}) {
        return apiClient.get<PageResult<ProductVariant>>(
            "/catalog/product-variants",
            {
                query: {
                    page: query.page,
                    size: query.size,
                    productId,
                    status: query.status
                }
            }
        );
    },
    detail(id: string) {
        return apiClient.get<ProductVariant>(
            `/catalog/product-variants/${encodeURIComponent(id)}`
        );
    },
    create(input: ProductVariantInput) {
        const { productId, ...body } = input;

        return apiClient.post<
            ProductVariant,
            Omit<ProductVariantInput, "productId">
        >(
            `/catalog/products/${encodeURIComponent(productId)}/variants`,
            body
        );
    },
    update(id: string, input: ProductVariantInput) {
        const body: Omit<ProductVariantInput, "productId"> = {
            quantity: input.quantity,
            sellingPrice: input.sellingPrice,
            costPrice: input.costPrice,
            unitId: input.unitId,
            inventoryPolicy: input.inventoryPolicy,
            status: input.status
        };

        return apiClient.put<
            ProductVariant,
            Omit<ProductVariantInput, "productId">
        >(
            `/catalog/product-variants/${encodeURIComponent(id)}`,
            body
        );
    },
    updateStatus(id: string, status: ProductVariant["status"]) {
        return apiClient.patch<
            ProductVariant,
            { status: ProductVariant["status"] }
        >(
            `/catalog/product-variants/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    updateSellableConfig(id: string, input: SellableConfigInput) {
        return apiClient.patch<ProductVariant, SellableConfigInput>(
            `/catalog/product-variants/${encodeURIComponent(id)}/sellable-config`,
            input
        );
    },
    export(productId?: string) {
        return apiClient.download("/catalog/product-variants/export", {
            query: productId && productId !== "ALL" ? { productId } : {}
        });
    },
    downloadTemplate() {
        return apiClient.download("/catalog/product-variants/import/template");
    },
    delete(id: string) {
        return apiClient.delete<void>(`/catalog/product-variants/${encodeURIComponent(id)}`);
    },
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResult<{ id: string; name: string }>>(
            "/catalog/product-variants/lookup",
            { query: { search, page, size } }
        );
    }
};
