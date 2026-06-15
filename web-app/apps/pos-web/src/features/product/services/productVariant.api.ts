import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    PageResult,
    ProductVariant,
    ProductVariantInput
} from "../types/product.types";

export interface ProductVariantQuery extends CatalogQuery {
    productId?: string;
    sku?: string;
    variantName?: string;
}

export const productVariantApi = {
    list(query: ProductVariantQuery = {}) {
        return apiClient.get<PageResult<ProductVariant>>(
            "/inventory/product-variants",
            {
                query: {
                    page: query.page,
                    size: query.size,
                    sku: query.sku ?? query.keyword,
                    variantName: query.variantName,
                    productId: query.productId,
                    status: query.status
                }
            }
        );
    },
    listByProduct(productId: string, query: CatalogQuery = {}) {
        return apiClient.get<PageResult<ProductVariant>>(
            "/inventory/product-variants",
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
            `/inventory/product-variants/${encodeURIComponent(id)}`
        );
    },
    create(input: ProductVariantInput) {
        const { productId, ...body } = input;

        return apiClient.post<
            ProductVariant,
            Omit<ProductVariantInput, "productId">
        >(
            `/products/${encodeURIComponent(productId)}/variants`,
            body
        );
    },
    update(id: string, input: ProductVariantInput) {
        const body: Omit<ProductVariantInput, "productId"> = {
            sku: input.sku,
            variantName: input.variantName,
            sellingPrice: input.sellingPrice,
            costPrice: input.costPrice,
            inventoryPolicy: input.inventoryPolicy,
            status: input.status
        };

        return apiClient.put<
            ProductVariant,
            Omit<ProductVariantInput, "productId">
        >(
            `/inventory/product-variants/${encodeURIComponent(id)}`,
            body
        );
    },
    updateStatus(id: string, status: ProductVariant["status"]) {
        return apiClient.patch<
            ProductVariant,
            { status: ProductVariant["status"] }
        >(
            `/inventory/product-variants/${encodeURIComponent(id)}/status`,
            { status }
        );
    }
};
